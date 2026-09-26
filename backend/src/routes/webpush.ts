// =============================================================================
// AILA — Web Push Routes (/api/webpush/*)
// Iscrizioni push della PWA. Rotte nuove: quelle esistenti (/api/fcm) non cambiano.
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware } from '../auth';
import { vapidKeysOf } from '../services/webpush';

const webPush = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

// Stesse categorie delle Impostazioni dell'app.
const KINDS = ['circulars', 'calendar', 'board', 'seatmap', 'polls'];

function cleanMutedKinds(raw: unknown): string {
  if (!Array.isArray(raw)) return '';
  return [...new Set(raw.filter((k): k is string => typeof k === 'string' && KINDS.includes(k)))].join(',');
}

// Solo endpoint https dei push service (niente URL arbitrari verso cui far chiamare il Worker).
function isValidEndpoint(endpoint: unknown): endpoint is string {
  if (typeof endpoint !== 'string' || endpoint.length > 1024) return false;
  try {
    return new URL(endpoint).protocol === 'https:';
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// GET /api/webpush/vapid-public-key — Chiave pubblica per pushManager.subscribe (pubblica)
// ---------------------------------------------------------------------------
webPush.get('/vapid-public-key', (c) => {
  const vapid = vapidKeysOf(c.env);
  if (!vapid) return c.json({ error: 'Web push non configurato sul server' }, 503);
  return c.json({ publicKey: vapid.publicKey });
});

webPush.use('/subscription', authMiddleware());
webPush.use('/preferences', authMiddleware());

// ---------------------------------------------------------------------------
// POST /api/webpush/subscription — Registra/aggiorna l'iscrizione di questo browser
// Body: { subscription: { endpoint, keys: { p256dh, auth } }, mutedKinds?: string[] }
// ---------------------------------------------------------------------------
webPush.post('/subscription', async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{
    subscription?: { endpoint?: unknown; keys?: { p256dh?: unknown; auth?: unknown } };
    mutedKinds?: unknown;
  }>();

  const endpoint = body.subscription?.endpoint;
  const p256dh = body.subscription?.keys?.p256dh;
  const auth = body.subscription?.keys?.auth;
  if (!isValidEndpoint(endpoint) || typeof p256dh !== 'string' || typeof auth !== 'string' || p256dh.length > 200 || auth.length > 100) {
    return c.json({ error: 'Iscrizione push non valida' }, 400);
  }

  // Upsert sull'endpoint: se il browser era di un altro account, passa a chi accede ora.
  await c.env.DB.prepare(
    `INSERT INTO web_push_subscriptions (endpoint, user_id, p256dh, auth, muted_kinds, updated_at)
     VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(endpoint) DO UPDATE SET user_id = excluded.user_id, p256dh = excluded.p256dh,
       auth = excluded.auth, muted_kinds = excluded.muted_kinds, updated_at = excluded.updated_at`
  ).bind(endpoint, payload.sub, p256dh, auth, cleanMutedKinds(body.mutedKinds)).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/webpush/subscription — Rimuove l'iscrizione (logout o notifiche spente)
// Body: { endpoint }
// ---------------------------------------------------------------------------
webPush.delete('/subscription', async (c) => {
  const payload = c.get('jwtPayload');
  const { endpoint } = await c.req.json<{ endpoint?: unknown }>();
  if (typeof endpoint !== 'string') return c.json({ error: 'endpoint mancante' }, 400);

  await c.env.DB.prepare('DELETE FROM web_push_subscriptions WHERE endpoint = ? AND user_id = ?')
    .bind(endpoint, payload.sub).run();
  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/webpush/preferences — Categorie silenziate per questo browser
// Body: { endpoint, mutedKinds: string[] }
// ---------------------------------------------------------------------------
webPush.put('/preferences', async (c) => {
  const payload = c.get('jwtPayload');
  const { endpoint, mutedKinds } = await c.req.json<{ endpoint?: unknown; mutedKinds?: unknown }>();
  if (typeof endpoint !== 'string') return c.json({ error: 'endpoint mancante' }, 400);

  const res = await c.env.DB.prepare(
    'UPDATE web_push_subscriptions SET muted_kinds = ?, updated_at = CURRENT_TIMESTAMP WHERE endpoint = ? AND user_id = ?'
  ).bind(cleanMutedKinds(mutedKinds), endpoint, payload.sub).run();

  if (!res.meta.changes) return c.json({ error: 'Iscrizione non trovata' }, 404);
  return c.json({ success: true });
});

export default webPush;
