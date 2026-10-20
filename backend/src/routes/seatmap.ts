// =============================================================================
// CIRCOLARE+ — Seat Map Routes (/api/seat-map/*)
// Pubblicazione e storico disposizione aula (finestra regressiva 4 mappe)
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
    'SELECT id, layout_json, published_at FROM seat_map_history WHERE class_id = ? ORDER BY published_at DESC LIMIT 1'
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

// ---------------------------------------------------------------------------
// GET /api/seat-map/history — Storico ultime 4 mappe
// ---------------------------------------------------------------------------
seatmap.get('/history', async (c) => {
  const rows = await c.env.DB.prepare(
    'SELECT id, map_index, layout_json, published_at FROM seat_map_history WHERE class_id = ? ORDER BY published_at DESC LIMIT 4'
  ).bind(await resolveClassId(c)).all<{ id: string; map_index: number; layout_json: string; published_at: string }>();

  return c.json({
    history: rows.results.map((r) => ({
      id: r.id,
      mapIndex: r.map_index,
      layout: JSON.parse(r.layout_json),
      publishedAt: r.published_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/seat-map/publish — Pubblica nuova mappa (solo REPRESENTATIVE)
// layoutJson: oggetto con la disposizione, es. { seats: [{studentId, row, col}, ...] }
// ---------------------------------------------------------------------------
seatmap.post('/publish', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{ layout: object }>();
  const classId = await resolveClassId(c);

  if (!body.layout || typeof body.layout !== 'object') {
    return c.json({ error: 'layout è richiesto e deve essere un oggetto JSON' }, 400);
  }

  // Storico a scorrimento (massimo 4 mappe): via la piu' vecchia, le altre scalano di uno, la
  // nuova diventa la 1. In un'unica transazione: prima erano tre query separate, e un errore a
  // meta' lasciava lo storico spostato senza la mappa nuova.
  const id = newUUID();
  await c.env.DB.batch([
    c.env.DB.prepare('DELETE FROM seat_map_history WHERE class_id = ? AND map_index >= 4').bind(classId),
    c.env.DB.prepare('UPDATE seat_map_history SET map_index = map_index + 1 WHERE class_id = ?').bind(classId),
    c.env.DB.prepare(
      'INSERT INTO seat_map_history (id, map_index, layout_json, class_id) VALUES (?, ?, ?, ?)'
    ).bind(id, 1, JSON.stringify(body.layout), classId),
  ]);

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

export default seatmap;
