import { Hono } from 'hono';
import { authMiddleware, requireRole, resolveClassId } from '../auth';
import { isValidIsoDate } from '../dates';
import { fullName } from '../names';
import { inBackground } from '../services/background';
import { notifyClass } from '../services/fcm';
import type { Env, JWTPayload } from '../types';

const bureaucracy = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();
const representativeOnly = requireRole('REPRESENTATIVE');
const MAX_PDF_BYTES = 10 * 1024 * 1024;
const materialKey = (id: string) => `bureaucracy/${id}.pdf`;

async function activePractice(env: Env, id: string, classId: string) {
  return env.DB.prepare(
    'SELECT 1 FROM bureaucracy_practices WHERE id = ? AND class_id = ? AND archived_at IS NULL'
  ).bind(id, classId).first();
}

async function removePdf(env: Env, id: string) {
  try {
    await env.CIRCULARS_BUCKET.delete(materialKey(id));
  } catch (error) {
    console.error('Could not remove bureaucracy PDF from R2', id, error);
  }
}

bureaucracy.use('*', authMiddleware(), async (c, next) => {
  c.header('Cache-Control', 'no-store');
  await next();
});

bureaucracy.get('/', async (c) => {
  const user = c.get('jwtPayload');
  const classId = await resolveClassId(c);
  const canManage = user.role === 'REPRESENTATIVE';
  const { results } = await c.env.DB.prepare(
    `SELECT p.id, p.title, p.description, p.due_date AS dueDate, p.archived_at AS archivedAt, d.received,
       (SELECT json_group_array(json_object('id', id, 'title', title, 'circularNumber', circular_number))
        FROM (
          SELECT m.id, COALESCE(m.file_name, circ.title) AS title, m.circular_number
          FROM bureaucracy_materials m
          LEFT JOIN circulars circ ON circ.number = m.circular_number
          WHERE m.practice_id = p.id AND (m.file_name IS NOT NULL OR circ.number IS NOT NULL)
          ORDER BY m.created_at, m.id
        )) AS materials
     FROM bureaucracy_practices p
     LEFT JOIN bureaucracy_deliveries d ON d.practice_id = p.id AND d.student_id = ?
     WHERE p.class_id = ? AND (? = 1 OR p.archived_at IS NULL OR d.student_id IS NOT NULL)
     ORDER BY COALESCE(p.archived_at, p.created_at) DESC, p.id DESC`
  ).bind(user.sub, classId, Number(canManage)).all<{
    id: string;
    title: string;
    description: string;
    archivedAt: string | null;
    dueDate: string | null;
    received: number | null;
    materials: string;
  }>();
  return c.json({
    canManage,
    practices: results.map((row) => ({
      ...row,
      materials: JSON.parse(row.materials) as { id: string; title: string; circularNumber: number | null }[],
      received: row.archivedAt && row.received === null ? null : Boolean(row.received),
    })),
  });
});

bureaucracy.post('/', representativeOnly, async (c) => {
  const body = await c.req.json<{ title?: unknown; description?: unknown; dueDate?: unknown } | null>();
  const title = typeof body?.title === 'string' ? body.title.trim() : '';
  const description = typeof body?.description === 'string' ? body.description.trim() : '';
  if (!title || title.length > 120) return c.json({ error: 'Il titolo deve contenere da 1 a 120 caratteri.' }, 400);
  if (description.length > 1000 || (body?.description !== undefined && typeof body.description !== 'string')) {
    return c.json({ error: 'Le istruzioni possono contenere al massimo 1000 caratteri.' }, 400);
  }
  const dueDate = body?.dueDate ?? null;
  if (dueDate !== null && (typeof dueDate !== 'string' || !isValidIsoDate(dueDate))) {
    return c.json({ error: 'Data di consegna non valida.' }, 400);
  }
  const id = crypto.randomUUID();
  const classId = await resolveClassId(c);
  await c.env.DB.prepare(
    'INSERT INTO bureaucracy_practices (id, class_id, title, description, due_date) VALUES (?, ?, ?, ?, ?)'
  ).bind(id, classId, title, description, dueDate).run();
  inBackground(c, notifyClass(c.env, 'Nuova pratica da consegnare', title, { action: 'bureaucracy_practice' }, classId));
  return c.json({ id }, 201);
});

bureaucracy.put('/:id/deadline', representativeOnly, async (c) => {
  const body = await c.req.json<{ dueDate?: unknown } | null>().catch(() => null);
  const dueDate = body?.dueDate;
  if (dueDate !== null && (typeof dueDate !== 'string' || !isValidIsoDate(dueDate))) {
    return c.json({ error: 'Data di consegna non valida.' }, 400);
  }
  const updated = await c.env.DB.prepare(
    'UPDATE bureaucracy_practices SET due_date = ? WHERE id = ? AND class_id = ? AND archived_at IS NULL'
  ).bind(dueDate, c.req.param('id'), await resolveClassId(c)).run();
  if (!updated.meta.changes) return c.json({ error: 'Pratica attiva non trovata.' }, 404);
  return c.json({ success: true });
});

bureaucracy.get('/:id/deliveries', representativeOnly, async (c) => {
  const id = c.req.param('id');
  const classId = await resolveClassId(c);
  const practice = await c.env.DB.prepare('SELECT 1 FROM bureaucracy_practices WHERE id = ? AND class_id = ?')
    .bind(id, classId).first();
  if (!practice) return c.json({ error: 'Pratica non trovata.' }, 404);

  const { results } = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, d.received
     FROM bureaucracy_practices p
     JOIN users u ON u.class_id = p.class_id
     LEFT JOIN bureaucracy_deliveries d ON d.practice_id = p.id AND d.student_id = u.id
     WHERE p.id = ? AND p.class_id = ? AND (p.archived_at IS NULL OR d.student_id IS NOT NULL)
     ORDER BY lower(u.last_name), lower(u.first_name), u.id`
  ).bind(id, classId).all<{ id: string; first_name: string; last_name: string; received: number | null }>();
  return c.json({
    deliveries: results.map((row) => ({
      studentId: row.id,
      name: fullName(row.first_name, row.last_name),
      received: Boolean(row.received),
    })),
  });
});

bureaucracy.put('/:id/deliveries/:studentId', representativeOnly, async (c) => {
  const body = await c.req.json<{ received?: unknown } | null>();
  if (typeof body?.received !== 'boolean') return c.json({ error: 'Stato della consegna non valido.' }, 400);
  const id = c.req.param('id');
  const studentId = c.req.param('studentId');
  const classId = await resolveClassId(c);
  // Stato esplicito per rendere la richiesta ripetibile. Il controllo sull'archivio fa parte della scrittura.
  const updated = await c.env.DB.prepare(
    `INSERT INTO bureaucracy_deliveries (practice_id, student_id, received)
     SELECT p.id, u.id, ? FROM bureaucracy_practices p
     JOIN users u ON u.class_id = p.class_id
     WHERE p.id = ? AND p.class_id = ? AND p.archived_at IS NULL AND u.id = ?
     ON CONFLICT(practice_id, student_id) DO UPDATE SET received = excluded.received`
  ).bind(Number(body.received), id, classId, studentId).run();
  if (updated.meta.changes === 0) {
    const practice = await c.env.DB.prepare('SELECT archived_at FROM bureaucracy_practices WHERE id = ? AND class_id = ?')
      .bind(id, classId).first<{ archived_at: string | null }>();
    if (practice?.archived_at) return c.json({ error: 'La pratica è già archiviata.' }, 409);
    return c.json({ error: 'Pratica o studente non trovati nella classe.' }, 404);
  }
  return c.json({ success: true });
});

bureaucracy.put('/:id/archive', representativeOnly, async (c) => {
  const id = c.req.param('id');
  const classId = await resolveClassId(c);
  // D1 esegue il batch in una transazione: anche le mancate consegne restano nell'archivio.
  const [, archived] = await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT OR IGNORE INTO bureaucracy_deliveries (practice_id, student_id, received)
       SELECT p.id, u.id, 0 FROM bureaucracy_practices p
       JOIN users u ON u.class_id = p.class_id
       WHERE p.id = ? AND p.class_id = ? AND p.archived_at IS NULL`
    ).bind(id, classId),
    c.env.DB.prepare(
      `UPDATE bureaucracy_practices SET archived_at = COALESCE(archived_at, CURRENT_TIMESTAMP)
       WHERE id = ? AND class_id = ?`
    ).bind(id, classId),
  ]);
  if (archived.meta.changes === 0) return c.json({ error: 'Pratica non trovata.' }, 404);
  return c.json({ success: true });
});

bureaucracy.post('/:id/materials/circulars', representativeOnly, async (c) => {
  const body = await c.req.json<{ number?: unknown } | null>().catch(() => null);
  const number = body?.number;
  if (typeof number !== 'number' || !Number.isSafeInteger(number) || number <= 0) {
    return c.json({ error: 'Numero di circolare non valido.' }, 400);
  }
  const classId = await resolveClassId(c);
  const practiceId = c.req.param('id')!;
  const result = await c.env.DB.prepare(
    `INSERT INTO bureaucracy_materials (id, practice_id, circular_number)
     SELECT ?, p.id, circ.number FROM bureaucracy_practices p CROSS JOIN circulars circ
     WHERE p.id = ? AND p.class_id = ? AND p.archived_at IS NULL AND circ.number = ?
     ON CONFLICT(practice_id, circular_number) DO NOTHING`
  ).bind(crypto.randomUUID(), practiceId, classId, number).run();
  if (!result.meta.changes) {
    if (!await activePractice(c.env, practiceId, classId)) {
      return c.json({ error: 'Pratica attiva non trovata.' }, 404);
    }
    if (!await c.env.DB.prepare('SELECT 1 FROM circulars WHERE number = ?').bind(number).first()) {
      return c.json({ error: 'Circolare non trovata.' }, 404);
    }
  }
  return c.json({ success: true });
});

bureaucracy.post('/:id/materials/documents', representativeOnly, async (c) => {
  const practiceId = c.req.param('id')!;
  const classId = await resolveClassId(c);
  if (!await activePractice(c.env, practiceId, classId)) {
    return c.json({ error: 'Pratica attiva non trovata.' }, 404);
  }
  const body = await c.req.parseBody().catch(() => null);
  const file = body?.file;
  if (!(file instanceof File) || file.type !== 'application/pdf' || file.size < 5 ||
      await file.slice(0, 5).text() !== '%PDF-') {
    return c.json({ error: 'Scegli un documento PDF valido.' }, 400);
  }
  if (file.size > MAX_PDF_BYTES) return c.json({ error: 'Il PDF supera il limite di 10 MB.' }, 413);
  const name = file.name.split(/[\\/]/).pop()?.trim().slice(0, 180) || 'Documento.pdf';
  const id = crypto.randomUUID();
  await c.env.CIRCULARS_BUCKET.put(materialKey(id), file.stream(), {
    httpMetadata: { contentType: 'application/pdf' },
  });
  try {
    const result = await c.env.DB.prepare(
      `INSERT INTO bureaucracy_materials (id, practice_id, file_name)
       SELECT ?, id, ? FROM bureaucracy_practices
       WHERE id = ? AND class_id = ? AND archived_at IS NULL`
    ).bind(id, name, practiceId, classId).run();
    if (result.meta.changes) return c.json({ success: true }, 201);
  } catch (error) {
    await removePdf(c.env, id);
    throw error;
  }
  await removePdf(c.env, id);
  return c.json({ error: 'Pratica attiva non trovata.' }, 404);
});

bureaucracy.get('/:id/materials/:materialId/file', async (c) => {
  const user = c.get('jwtPayload');
  const material = await c.env.DB.prepare(
    `SELECT m.id FROM bureaucracy_materials m
     JOIN bureaucracy_practices p ON p.id = m.practice_id
     LEFT JOIN bureaucracy_deliveries d ON d.practice_id = p.id AND d.student_id = ?
     WHERE p.class_id = ? AND (? = 1 OR p.archived_at IS NULL OR d.student_id IS NOT NULL)
       AND p.id = ? AND m.id = ? AND m.file_name IS NOT NULL`
  ).bind(user.sub, await resolveClassId(c), Number(user.role === 'REPRESENTATIVE'),
    c.req.param('id'), c.req.param('materialId')).first<{ id: string }>();
  const object = material && await c.env.CIRCULARS_BUCKET.get(materialKey(material.id));
  if (!object) return c.json({ error: 'Documento non trovato.' }, 404);
  return new Response(object.body, {
    headers: {
      'Content-Type': 'application/pdf',
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
    },
  });
});

bureaucracy.delete('/:id/materials/:materialId', representativeOnly, async (c) => {
  const material = await c.env.DB.prepare(
    `DELETE FROM bureaucracy_materials WHERE id = ? AND practice_id IN (
       SELECT id FROM bureaucracy_practices WHERE id = ? AND class_id = ? AND archived_at IS NULL
     ) RETURNING id, file_name`
  ).bind(c.req.param('materialId'), c.req.param('id'), await resolveClassId(c))
    .first<{ id: string; file_name: string | null }>();
  if (!material) return c.json({ error: 'Materiale non trovato in una pratica attiva.' }, 404);
  if (material.file_name !== null) await removePdf(c.env, material.id);
  return c.json({ success: true });
});

export default bureaucracy;
