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

  // Rotate history: shift map indices
  // Current maps: N-1 → N-2 → N-3 → N-4 (max 4 stored)
  // Before inserting the new one, shift existing records
  await c.env.DB.prepare(
    'DELETE FROM seat_map_history WHERE class_id = ? AND map_index >= 4'
  ).bind(classId).run();

  // Shift existing indices: 3→4, 2→3, 1→2
  await c.env.DB.prepare(
    'UPDATE seat_map_history SET map_index = map_index + 1 WHERE class_id = ?'
  ).bind(classId).run();

  // Insert the new map as index 1 (current / N-1)
  const id = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO seat_map_history (id, map_index, layout_json, class_id) VALUES (?, ?, ?, ?)'
  ).bind(id, 1, JSON.stringify(body.layout), classId).run();

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
