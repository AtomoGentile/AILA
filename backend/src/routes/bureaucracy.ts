import { Hono } from 'hono';
import { authMiddleware, requireRole, resolveClassId } from '../auth';
import { fullName } from '../names';
import type { Env, JWTPayload } from '../types';

const bureaucracy = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();
const representativeOnly = requireRole('REPRESENTATIVE');

bureaucracy.use('*', authMiddleware(), async (c, next) => {
  c.header('Cache-Control', 'no-store');
  await next();
});

bureaucracy.get('/', async (c) => {
  const user = c.get('jwtPayload');
  const { results } = await c.env.DB.prepare(
    `SELECT p.id, p.title, p.description, p.archived_at AS archivedAt, d.received
     FROM bureaucracy_practices p
     LEFT JOIN bureaucracy_deliveries d ON d.practice_id = p.id AND d.student_id = ?
     WHERE p.class_id = ? AND (? = 1 OR p.archived_at IS NULL OR d.student_id IS NOT NULL)
     ORDER BY COALESCE(p.archived_at, p.created_at) DESC, p.id DESC`
  ).bind(user.sub, await resolveClassId(c), Number(user.role === 'REPRESENTATIVE')).all<{
    id: string;
    title: string;
    description: string;
    archivedAt: string | null;
    received: number | null;
  }>();
  return c.json({
    canManage: user.role === 'REPRESENTATIVE',
    practices: results.map((row) => ({
      ...row,
      received: row.archivedAt && row.received === null ? null : Boolean(row.received),
    })),
  });
});

bureaucracy.post('/', representativeOnly, async (c) => {
  const body = await c.req.json<{ title?: unknown; description?: unknown } | null>();
  const title = typeof body?.title === 'string' ? body.title.trim() : '';
  const description = typeof body?.description === 'string' ? body.description.trim() : '';
  if (!title || title.length > 120) return c.json({ error: 'Il titolo deve contenere da 1 a 120 caratteri.' }, 400);
  if (description.length > 1000 || (body?.description !== undefined && typeof body.description !== 'string')) {
    return c.json({ error: 'Le istruzioni possono contenere al massimo 1000 caratteri.' }, 400);
  }
  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    'INSERT INTO bureaucracy_practices (id, class_id, title, description) VALUES (?, ?, ?, ?)'
  ).bind(id, await resolveClassId(c), title, description).run();
  return c.json({ id }, 201);
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

export default bureaucracy;
