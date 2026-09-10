// =============================================================================
// CIRCOLARE+ — FCM Token Routes (/api/fcm/*)
// Registrazione e gestione token dispositivi per notifiche push
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware } from '../auth';

const fcmRoutes = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

fcmRoutes.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// POST /api/fcm/token — Registra/aggiorna token dispositivo
// ---------------------------------------------------------------------------
fcmRoutes.post('/token', async (c) => {
  const payload = c.get('jwtPayload');
  const { token, platform } = await c.req.json<{ token: string; platform: 'android' | 'ios' }>();

  if (!token) return c.json({ error: 'token è obbligatorio' }, 400);
  if (!['android', 'ios'].includes(platform)) {
    return c.json({ error: "platform deve essere 'android' o 'ios'" }, 400);
  }

  // Upsert: one token per user per platform
  await c.env.DB.prepare(
    `INSERT INTO fcm_tokens (user_id, token, platform, updated_at)
     VALUES (?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(user_id, platform) DO UPDATE SET token = excluded.token, updated_at = excluded.updated_at`
  ).bind(payload.sub, token, platform).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/fcm/token — Rimuovi token (logout)
// ---------------------------------------------------------------------------
fcmRoutes.delete('/token', async (c) => {
  const payload = c.get('jwtPayload');
  const { platform } = await c.req.json<{ platform?: 'android' | 'ios' }>();

  if (platform) {
    await c.env.DB.prepare(
      'DELETE FROM fcm_tokens WHERE user_id = ? AND platform = ?'
    ).bind(payload.sub, platform).run();
  } else {
    // Remove all tokens for this user (full logout)
    await c.env.DB.prepare('DELETE FROM fcm_tokens WHERE user_id = ?').bind(payload.sub).run();
  }

  return c.json({ success: true });
});

export default fcmRoutes;
