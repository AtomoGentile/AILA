// =============================================================================
// CIRCOLARE+ — Seat Map Routes (/api/seat-map/*)
// Pubblicazione e archivio delle disposizioni effettivamente pubblicate.
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass } from '../services/fcm';
import { inBackground } from '../services/background';

const seatmap = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

seatmap.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// GET /api/seat-map/current — Mappa attuale pubblicata
// ---------------------------------------------------------------------------
seatmap.get('/current', async (c) => {
  const row = await c.env.DB.prepare(
    'SELECT id, layout_json, published_at FROM seat_map_history WHERE class_id = ? ORDER BY map_index ASC LIMIT 1'
  ).bind(await resolveClassId(c)).first<{ id: string; layout_json: string; published_at: string }>();

  if (!row) return c.json({ seatMap: null });

  return c.json({
    seatMap: {
      id: row.id,
      layout: JSON.parse(row.layout_json),
      publishedAt: row.published_at,
    },
  });
});

interface RotationPolicy {
  rowCycleMaps: number | null;
  companionWaitMaps: number;
}

seatmap.get('/history', async (c) => {
  const archive = c.req.query('archive') === 'true';
  const optimizer = c.req.query('rotation') === 'true';
  const classId = await resolveClassId(c);
  type HistoryRow = {
    id: string | null; map_index: number; layout_json: string; published_at: string;
    rowCycleMaps?: number | null; companionWaitMaps?: number; publishedCount?: number; rotationRevision?: number;
  };
  const rows = optimizer
    ? await c.env.DB.prepare(`WITH rotation_config AS (
        SELECT id, seat_row_cycle AS rowCycleMaps, seat_companion_wait AS companionWaitMaps, seat_rotation_revision AS rotationRevision,
          (SELECT COALESCE(MAX(map_index), 0) FROM seat_map_history WHERE class_id = classes.id) AS publishedCount
        FROM classes WHERE id = ?
      )
      SELECT history.id, history.map_index, history.layout_json, history.published_at,
        rotation_config.rowCycleMaps, rotation_config.companionWaitMaps, rotation_config.publishedCount, rotation_config.rotationRevision
      FROM rotation_config LEFT JOIN seat_map_history history ON history.class_id = rotation_config.id
        AND (? OR history.map_index <= 30 OR history.published_at >= datetime('now', '-12 months'))
      ORDER BY history.map_index ASC`).bind(classId, Number(archive)).all<HistoryRow>()
    : await c.env.DB.prepare(
        'SELECT id, map_index, layout_json, published_at FROM seat_map_history WHERE class_id = ?' +
        (archive ? '' : " AND (map_index <= 4 OR published_at >= datetime('now', '-12 months'))") +
        ' ORDER BY map_index ASC'
      ).bind(classId).all<HistoryRow>();
  const rotation = optimizer ? rows.results[0] : undefined;
  return c.json({
    history: rows.results.filter(r => r.id !== null).map((r) => ({
      id: r.id,
      mapIndex: r.map_index,
      layout: JSON.parse(r.layout_json),
      publishedAt: r.published_at,
    })),
    ...(rotation && {
      rotation: { rowCycleMaps: rotation.rowCycleMaps, companionWaitMaps: rotation.companionWaitMaps },
      publishedCount: rotation.publishedCount,
      rotationRevision: rotation.rotationRevision
    })
  });
});

seatmap.put('/rotation', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<RotationPolicy | null>().catch(() => null);
  const validCount = (count: number, minimum: number) => Number.isInteger(count) && count >= minimum && count <= 30;
  if (!body || (body.rowCycleMaps !== null && !validCount(body.rowCycleMaps, 1)) || !validCount(body.companionWaitMaps, 0)) {
    return c.json({ error: 'Durata della rotazione non valida.' }, 400);
  }
  await c.env.DB.prepare(`UPDATE classes SET seat_row_cycle = ?, seat_companion_wait = ?,
    seat_rotation_revision = seat_rotation_revision + 1 WHERE id = ?`)
    .bind(body.rowCycleMaps, body.companionWaitMaps, await resolveClassId(c)).run();
  return c.json({ rowCycleMaps: body.rowCycleMaps, companionWaitMaps: body.companionWaitMaps });
});

// ---------------------------------------------------------------------------
// DELETE /api/seat-map/history/:id — Elimina una pubblicazione della propria classe
// ---------------------------------------------------------------------------
seatmap.delete('/history/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const id = c.req.param('id');
  // Legge l'indice nella stessa transazione della cancellazione: anche due rappresentanti
  // che intervengono insieme mantengono gli indici consecutivi e la mappa attuale corretta.
  const results = await c.env.DB.batch([
    c.env.DB.prepare(`UPDATE seat_map_history SET map_index = map_index - 1
      WHERE class_id = ? AND map_index >
        (SELECT map_index FROM seat_map_history WHERE id = ? AND class_id = ?)`)
      .bind(classId, id, classId),
    c.env.DB.prepare('DELETE FROM seat_map_history WHERE id = ? AND class_id = ?').bind(id, classId),
  ]);
  if (!results[1].meta.changes) return c.json({ error: 'Mappa pubblicata non trovata' }, 404);
  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/seat-map/publish — Pubblica nuova mappa (solo REPRESENTATIVE)
// layoutJson: oggetto con la disposizione, es. { seats: [{studentId, row, col}, ...] }
// ---------------------------------------------------------------------------
seatmap.post('/publish', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ layout: object; rotationRevision?: number | null }>();
  const classId = await resolveClassId(c);

  if (!body.layout || typeof body.layout !== 'object') {
    return c.json({ error: 'layout è richiesto e deve essere un oggetto JSON' }, 400);
  }

  const revision = body.rotationRevision ?? null;
  if (revision !== null && (!Number.isSafeInteger(revision) || revision < 0)) {
    return c.json({ error: 'Versione della rotazione non valida.' }, 400);
  }

  // Controlla e pubblica nella stessa transazione. Le bozze non entrano nello storico.
  const id = newUUID();
  const [inserted] = await c.env.DB.batch([
    c.env.DB.prepare(`INSERT INTO seat_map_history (id, map_index, layout_json, class_id)
      SELECT ?, 1, ?, id FROM classes WHERE id = ? AND (? IS NULL OR seat_rotation_revision = ?)`)
      .bind(id, JSON.stringify(body.layout), classId, revision, revision),
    c.env.DB.prepare(`UPDATE seat_map_history SET map_index = map_index + 1
      WHERE class_id = ? AND id <> ? AND EXISTS (SELECT 1 FROM seat_map_history WHERE id = ?)`)
      .bind(classId, id, id),
  ]);
  if (!inserted.meta.changes) {
    return c.json({ error: 'Le regole o lo storico delle mappe sono cambiati. Riprova con la disposizione aggiornata.' }, 409);
  }

  // Push notification to class
  inBackground(c, notifyClass(
    c.env,
    'Nuova Mappa Posti Pubblicata',
    'Il Rappresentante ha pubblicato la nuova disposizione dei banchi. Scopri il tuo posto!',
    { action: 'seat_map_updated' },
    classId
  ));

  return c.json({ success: true, id }, 201);
});

// ---------------------------------------------------------------------------
// Fantocci della Mappa Posti
//
// Studenti inventati dal Rappresentante che occupano un posto come tutti, con
// nome e cognome in mappa, ma non sono utenti: nessuna credenziale, nessun
// accesso, e di conseguenza non votano le preferenze sociali e non possono
// essere votati (la rotta di voto li respinge esplicitamente, vedi preferences.ts).
// Servono ad esempio per riempire le file quando i compagni reali sono pochi.
// ---------------------------------------------------------------------------

// Un solo fantoccio per classe fino a questo numero: sono nomi fittizi, non un
// registro anagrafico, e la capienza reale di un'aula resta dell'ordine delle
// decine.
export const MAX_DUMMIES = 30;

function normalizeName(value: unknown): string {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : '';
}

seatmap.get('/dummies', async (c) => {
  const rows = await c.env.DB.prepare(
    'SELECT id, first_name, last_name FROM class_dummies WHERE class_id = ? ORDER BY last_name, first_name'
  ).bind(await resolveClassId(c)).all<{ id: string; first_name: string; last_name: string }>();

  return c.json({
    dummies: rows.results.map((r) => ({
      id: r.id,
      firstName: r.first_name,
      lastName: r.last_name,
    })),
  });
});

seatmap.post('/dummies', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ firstName?: unknown; lastName?: unknown }>().catch(() => ({}) as { firstName?: unknown; lastName?: unknown });
  const firstName = normalizeName(body.firstName);
  const lastName = normalizeName(body.lastName);

  if (!firstName || !lastName) {
    return c.json({ error: 'Nome e cognome sono richiesti' }, 400);
  }
  // Le colonne sono TEXT NOT NULL senza limite: il tetto serve a non ritrovarsi
  // un "nome" di dieci migliaia di caratteri in mappa e nel PDF esportato.
  if (firstName.length > 30 || lastName.length > 30) {
    return c.json({ error: 'Nome e cognome possono avere al massimo 30 caratteri' }, 400);
  }

  const classId = await resolveClassId(c);
  const count = await c.env.DB.prepare(
    'SELECT COUNT(*) as cnt FROM class_dummies WHERE class_id = ?'
  ).bind(classId).first<{ cnt: number }>();
  if ((count?.cnt ?? 0) >= MAX_DUMMIES) {
    return c.json({ error: `Puoi creare al massimo ${MAX_DUMMIES} fantocci per classe` }, 409);
  }

  const id = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO class_dummies (id, class_id, first_name, last_name, created_by) VALUES (?, ?, ?, ?, ?)'
  ).bind(id, classId, firstName, lastName, c.get('jwtPayload').sub).run();

  return c.json({
    success: true,
    dummy: { id, firstName, lastName },
  }, 201);
});

seatmap.delete('/dummies/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const id = c.req.param('id');
  // Solo i fantocci della propria classe: la richiesta di un'altra classe per
  // id esatto resterebbe comunque senza effetto, ma l'errore distinto e' piu'
  // onesto del 200 fatto a vuoto.
  const classId = await resolveClassId(c);
  const result = await c.env.DB.prepare('DELETE FROM class_dummies WHERE id = ? AND class_id = ?')
    .bind(id, classId).run();
  if (!result.meta.changes) return c.json({ error: 'Fantoccio non trovato' }, 404);
  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/seat-map/reset-history — Reset storico mappe (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
seatmap.post('/reset-history', requireRole('REPRESENTATIVE'), async (c) => {
  // Solo lo storico della propria classe: prima azzerava quello di tutte.
  await c.env.DB.prepare('DELETE FROM seat_map_history WHERE class_id = ?')
    .bind(await resolveClassId(c))
    .run();
  return c.json({ success: true, message: 'Storico mappe azzerato' });
});

// Inserimento e correzione retroattiva: gli indici seguono la data effettiva.
function validLayout(value: unknown): value is Array<Record<string, unknown>> {
  if (!Array.isArray(value) || value.length === 0 || value.length > 120) return false;
  const ids = new Set<string>();
  const positions = new Set<string>();
  return value.every((desk) => {
    if (!desk || !Number.isInteger(desk.row) || desk.row < 0 || desk.row > 30 ||
        !Number.isInteger(desk.column) || desk.column < 0 || desk.column > 30 ||
        (desk.seats !== undefined && desk.seats !== 2 && desk.seats !== 3)) return false;
    const position = `${desk.row}:${desk.column}`;
    if (positions.has(position)) return false;
    positions.add(position);
    return ['studentAId', 'studentBId', 'studentCId'].every((key) => {
      const id = desk[key];
      if (id == null) return true;
      if (key === 'studentCId' && desk.seats !== 3) return false;
      if (typeof id !== 'string' || !id || ids.has(id)) return false;
      ids.add(id);
      return true;
    });
  });
}
async function classLayoutIds(env: Env, classId: string): Promise<Set<string>> {
  const members = await env.DB.prepare(`SELECT id FROM users WHERE class_id = ?
    UNION SELECT id FROM class_dummies WHERE class_id = ?`).bind(classId, classId).all<{ id: string }>();
  const ids = new Set(members.results.map(r => r.id));
  const maps = await env.DB.prepare('SELECT layout_json FROM seat_map_history WHERE class_id = ?')
    .bind(classId).all<{ layout_json: string }>();
  for (const map of maps.results) {
    const layout = JSON.parse(map.layout_json);
    if (!Array.isArray(layout)) continue;
    for (const desk of layout) for (const key of ['studentAId', 'studentBId', 'studentCId']) {
      if (typeof desk?.[key] === 'string') ids.add(desk[key]);
    }
  }
  return ids;
}
function knownLayout(layout: Array<Record<string, unknown>>, ids: Set<string>): boolean {
  return layout.every(d => ['studentAId', 'studentBId', 'studentCId'].every(k => d[k] == null || ids.has(d[k] as string)));
}

const reindex = (env: Env, classId: string) => env.DB.prepare(`
  WITH ordered AS (SELECT id, ROW_NUMBER() OVER (ORDER BY published_at DESC, map_index ASC, id) AS n
    FROM seat_map_history WHERE class_id = ?)
  UPDATE seat_map_history SET map_index = (SELECT n FROM ordered WHERE ordered.id = seat_map_history.id)
  WHERE class_id = ?`).bind(classId, classId);

seatmap.post('/history', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ layout: unknown; publishedAt: string }>();
  const date = new Date(body.publishedAt);
  if (!validLayout(body.layout) || typeof body.publishedAt !== 'string' || !Number.isFinite(date.getTime()) || date.getTime() > Date.now() || date.toISOString().slice(0, 10) !== body.publishedAt.slice(0, 10)) {
    return c.json({ error: 'Disposizione o data passata non valida' }, 400);
  }
  const classId = await resolveClassId(c);
  if (!knownLayout(body.layout, await classLayoutIds(c.env, classId))) return c.json({ error: 'La mappa contiene studenti estranei alla classe' }, 400);
  const id = newUUID();
  await c.env.DB.batch([
    c.env.DB.prepare('INSERT INTO seat_map_history (id, map_index, layout_json, class_id, published_at) VALUES (?, 1, ?, ?, ?)')
      .bind(id, JSON.stringify(body.layout), classId, date.toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, '')),
    reindex(c.env, classId),
  ]);
  return c.json({ success: true, id }, 201);
});

seatmap.put('/history/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ layout: unknown }>();
  if (!validLayout(body.layout)) return c.json({ error: 'Disposizione non valida: controlla posti e duplicati' }, 400);
  const classId = await resolveClassId(c);
  const existing = await c.env.DB.prepare('SELECT id FROM seat_map_history WHERE id = ? AND class_id = ?').bind(c.req.param('id'), classId).first();
  if (!existing) return c.json({ error: 'Mappa non trovata' }, 404);
  if (!knownLayout(body.layout, await classLayoutIds(c.env, classId))) return c.json({ error: 'La mappa contiene studenti estranei alla classe' }, 400);
  const result = await c.env.DB.prepare('UPDATE seat_map_history SET layout_json = ? WHERE id = ? AND class_id = ?')
    .bind(JSON.stringify(body.layout), c.req.param('id'), classId).run();
  if (!result.meta.changes) return c.json({ error: 'Mappa non trovata' }, 404);
  return c.json({ success: true });
});

// Riconcilia solo un identificativo non più registrato, su scelta esplicita del rappresentante.
seatmap.post('/relink-student', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ previousId: string; studentId: string }>();
  const classId = await resolveClassId(c);
  if (typeof body.previousId !== 'string' || typeof body.studentId !== 'string' || body.previousId === body.studentId) {
    return c.json({ error: 'Identificativi non validi' }, 400);
  }
  const oldUser = await c.env.DB.prepare('SELECT id FROM users WHERE id = ?').bind(body.previousId).first();
  const target = await c.env.DB.prepare('SELECT id FROM users WHERE id = ? AND class_id = ?').bind(body.studentId, classId).first();
  const oldDummy = await c.env.DB.prepare('SELECT id FROM class_dummies WHERE id = ?').bind(body.previousId).first();
  if (oldUser || oldDummy || !target) return c.json({ error: 'Scegli un account precedente eliminato e uno studente della classe' }, 400);
  const rows = await c.env.DB.prepare('SELECT layout_json FROM seat_map_history WHERE class_id = ?').bind(classId).all<{ layout_json: string }>();
  let found = false;
  for (const row of rows.results) {
    const layout = JSON.parse(row.layout_json);
    if (!Array.isArray(layout)) continue;
    const ids = layout.flatMap(d => [d.studentAId, d.studentBId, d.studentCId]);
    if (ids.includes(body.previousId)) {
      found = true;
      if (ids.includes(body.studentId)) return c.json({ error: 'I due studenti sono presenti nella stessa mappa: correggila prima del collegamento' }, 409);
    }
  }
  if (!found) return c.json({ error: 'Account precedente non trovato nelle mappe della classe' }, 404);
  await c.env.DB.batch([
    c.env.DB.prepare('UPDATE seat_map_history SET layout_json = REPLACE(layout_json, ?, ?) WHERE class_id = ?')
      .bind(JSON.stringify(body.previousId), JSON.stringify(body.studentId), classId),

  ]);
  return c.json({ success: true });
});

export default seatmap;
