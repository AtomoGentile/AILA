// =============================================================================
// CIRCOLARE+ — FCM Token Routes (/api/fcm/*)
// Registrazione e gestione token dispositivi per notifiche push
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, resolveClassId } from '../auth';
import { topicName } from '../services/fcm';

const fcmRoutes = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

fcmRoutes.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// POST /api/fcm/token — Registra/aggiorna token dispositivo
// ---------------------------------------------------------------------------
fcmRoutes.post('/token', async (c) => {
  const payload = c.get('jwtPayload');
  const { token, platform, mutedKinds, systemNotifications } = await c.req.json<{
    token: string;
    platform: 'android' | 'ios';
    // Preferenze delle Impostazioni del telefono (facoltative: le app vecchie non le mandano).
    // Servono ai messaggi iOS, vedi buildMessage in services/fcm.ts.
    mutedKinds?: string[];
    systemNotifications?: boolean;
  }>();

  if (!token) return c.json({ error: 'token è obbligatorio' }, 400);
  if (!['android', 'ios'].includes(platform)) {
    return c.json({ error: "platform deve essere 'android' o 'ios'" }, 400);
  }

  // Un token FCM identifica un dispositivo, non un utente: se sullo stesso telefono si entra con
  // due account (Rappresentante e studente, com'è normale provando l'app) il token finiva
  // registrato per entrambi e ogni notifica di classe arrivava due volte. Chi accede per ultimo
  // ne diventa l'unico proprietario.
  // Un token nuovo per questo utente (di un altro account, o mai visto) non e' piu' iscritto ai
  // topic di prima: si toglie dal registro PRIMA di riassegnarlo. Non deve far fallire la
  // registrazione se la migrazione 014 manca.
  try {
    await c.env.DB.prepare(
      'DELETE FROM fcm_topic_devices WHERE token = ? AND token NOT IN (SELECT token FROM fcm_tokens WHERE user_id = ?)'
    ).bind(token, payload.sub).run();
  } catch (e) {
    console.error('[FCM] Registro topic non aggiornato (manca la migrazione 014?)', e);
  }

  await c.env.DB.batch([
    c.env.DB.prepare('DELETE FROM fcm_tokens WHERE token = ? AND user_id != ?').bind(token, payload.sub),
    // Upsert: one token per user per platform
    c.env.DB.prepare(
      `INSERT INTO fcm_tokens (user_id, token, platform, updated_at)
       VALUES (?, ?, ?, CURRENT_TIMESTAMP)
       ON CONFLICT(user_id, platform) DO UPDATE SET token = excluded.token, updated_at = excluded.updated_at`
    ).bind(payload.sub, token, platform),
  ]);

  // I token sostituiti dall'upsert non esistono piu': via anche dal registro.
  try {
    await c.env.DB.prepare('DELETE FROM fcm_topic_devices WHERE token NOT IN (SELECT token FROM fcm_tokens)').run();
  } catch (e) {
    console.error('[FCM] Registro topic non aggiornato (manca la migrazione 014?)', e);
  }

  if (Array.isArray(mutedKinds) || typeof systemNotifications === 'boolean') {
    const muted = (Array.isArray(mutedKinds) ? mutedKinds : [])
      .filter((k) => typeof k === 'string' && /^[a-z_]{1,32}$/.test(k))
      .join(',');
    try {
      await c.env.DB.prepare(
        'UPDATE fcm_tokens SET muted_kinds = ?, system_notifications = ? WHERE user_id = ? AND platform = ?'
      ).bind(muted, systemNotifications === false ? 0 : 1, payload.sub, platform).run();
    } catch (e) {
      // Migrazione 009 non ancora applicata: il token e' comunque registrato, le notifiche
      // arrivano come prima (senza filtri lato server).
      console.error('[FCM] Preferenze non salvate (manca la migrazione 009?)', e);
    }
  }

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/fcm/token — Rimuovi token (logout)
// ---------------------------------------------------------------------------
fcmRoutes.delete('/token', async (c) => {
  const payload = c.get('jwtPayload');
  const { platform } = await c.req.json<{ platform?: 'android' | 'ios' }>();

  // Prima del token, altrimenti la sottoquery non trova piu' nulla.
  try {
    await c.env.DB.prepare(
      `DELETE FROM fcm_topic_devices WHERE token IN
         (SELECT token FROM fcm_tokens WHERE user_id = ?${platform ? ' AND platform = ?' : ''})`
    ).bind(...(platform ? [payload.sub, platform] : [payload.sub])).run();
  } catch (e) {
    console.error('[FCM] Registro topic non aggiornato (manca la migrazione 014?)', e);
  }

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

// ---------------------------------------------------------------------------
// GET /api/fcm/topics — Nomi (segreti) dei topic a cui iscriversi: istituto e classe
// ---------------------------------------------------------------------------
fcmRoutes.get('/topics', async (c) => {
  const classId = await resolveClassId(c);
  return c.json({
    school: await topicName(c.env, 'school'),
    class: await topicName(c.env, { classId }),
  });
});

// ---------------------------------------------------------------------------
// POST /api/fcm/topics/subscribed — L'app si e' iscritta ai topic: da ora non riceve piu' anche
// il messaggio per token. Vale solo per un token dell'utente stesso.
// ---------------------------------------------------------------------------
fcmRoutes.post('/topics/subscribed', async (c) => {
  const payload = c.get('jwtPayload');
  const { token } = await c.req.json<{ token?: string }>().catch(() => ({ token: undefined }));
  if (!token) return c.json({ error: 'token è obbligatorio' }, 400);

  try {
    await c.env.DB.prepare(
      `INSERT INTO fcm_topic_devices (token, subscribed_at)
       SELECT token, CURRENT_TIMESTAMP FROM fcm_tokens WHERE user_id = ? AND token = ? LIMIT 1
       ON CONFLICT(token) DO UPDATE SET subscribed_at = excluded.subscribed_at`
    ).bind(payload.sub, token).run();
  } catch (e) {
    // Migrazione 014 non ancora applicata: l'app resta iscritta ma il server continua per token.
    console.error('[FCM] Iscrizione ai topic non registrata (manca la migrazione 014?)', e);
  }
  return c.json({ success: true });
});

export default fcmRoutes;
