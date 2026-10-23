// =============================================================================
// CIRCOLARE+ — Telegram Routes (rotte /api/telegram)
// =============================================================================

import { Hono } from 'hono';
import type { Context } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, randomCode, timingSafeEqual } from '../auth';
import { sendTelegramMessage } from '../services/telegram';

const telegramRoutes = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

const CODE_TTL_SECONDS = 15 * 60; // 15 minuti

// ---------------------------------------------------------------------------
// POST /api/telegram/link-code — Genera un codice temporaneo per collegare Telegram
// ---------------------------------------------------------------------------
telegramRoutes.post('/link-code', authMiddleware(), async (c) => {
  const payload = c.get('jwtPayload');
  const code = randomCode(6);
  const expiresAt = Math.floor(Date.now() / 1000) + CODE_TTL_SECONDS;

  try {
    // Elimina eventuali vecchi codici non ancora usati dell'utente prima di crearne uno nuovo
    await c.env.DB.batch([
      c.env.DB.prepare('DELETE FROM telegram_link_codes WHERE user_id = ? AND used_at IS NULL').bind(payload.sub),
      c.env.DB.prepare(
        `INSERT INTO telegram_link_codes (code, user_id, expires_at, created_at)
         VALUES (?, ?, ?, CURRENT_TIMESTAMP)`
      ).bind(code, payload.sub, expiresAt),
    ]);
  } catch {
    return c.json({ error: 'Funzione Telegram non ancora attiva sul server (manca la migrazione 022)' }, 503);
  }

  return c.json({
    code,
    expiresAt: new Date(expiresAt * 1000).toISOString(),
  });
});

// ---------------------------------------------------------------------------
// GET /api/telegram/status — Stato del collegamento Telegram
// ---------------------------------------------------------------------------
telegramRoutes.get('/status', authMiddleware(), async (c) => {
  const payload = c.get('jwtPayload');

  const link = await c.env.DB.prepare(
    'SELECT chat_id, linked_at, active FROM telegram_links WHERE user_id = ?'
  ).bind(payload.sub).first<{ chat_id: string; linked_at: string; active: number }>().catch(() => null);

  const user = await c.env.DB.prepare(
    'SELECT signature_expires_at FROM users WHERE id = ?'
  ).bind(payload.sub).first<{ signature_expires_at: string | null }>().catch(() => null);

  return c.json({
    linked: Boolean(link),
    active: Boolean(link?.active),
    linkedAt: link?.linked_at || null,
    signatureExpiresAt: user?.signature_expires_at || null,
  });
});

// ---------------------------------------------------------------------------
// POST /api/telegram/unlink — Disabilita/Scollega il bot Telegram
// ---------------------------------------------------------------------------
telegramRoutes.post('/unlink', authMiddleware(), async (c) => {
  const payload = c.get('jwtPayload');

  await c.env.DB.prepare('UPDATE telegram_links SET active = 0 WHERE user_id = ?')
    .bind(payload.sub)
    .run();

  return c.json({ success: true, active: false });
});

// ---------------------------------------------------------------------------
// POST /api/telegram/webhook — Webhook del Bot Telegram
// ---------------------------------------------------------------------------
telegramRoutes.post('/webhook', async (c) => {
  // Verifica il segreto del webhook se configurato
  if (c.env.TELEGRAM_WEBHOOK_SECRET) {
    const secretHeader = c.req.header('x-telegram-bot-api-secret-token') || '';
    if (!timingSafeEqual(secretHeader, c.env.TELEGRAM_WEBHOOK_SECRET)) {
      return c.json({ error: 'Segreto non valido' }, 401);
    }
  }

  let body: any;
  try {
    body = await c.req.json();
  } catch {
    return c.json({ ok: true }); // Rispondi sempre 200 a Telegram
  }

  const message = body?.message || body?.edited_message;
  if (!message || !message.chat || !message.chat.id) {
    return c.json({ ok: true });
  }

  const chatId = String(message.chat.id);
  const text = typeof message.text === 'string' ? message.text.trim() : '';

  if (!text) {
    return c.json({ ok: true });
  }

  // Comando /stop: disattiva i promemoria per questa chat
  if (text.toLowerCase() === '/stop') {
    const link = await c.env.DB.prepare('SELECT user_id FROM telegram_links WHERE chat_id = ? AND active = 1')
      .bind(chatId)
      .first<{ user_id: string }>()
      .catch(() => null);

    if (link) {
      await c.env.DB.prepare('UPDATE telegram_links SET active = 0 WHERE chat_id = ?')
        .bind(chatId)
        .run();

      if (c.env.TELEGRAM_TOKEN) {
        await sendTelegramMessage(
          c.env.TELEGRAM_TOKEN,
          chatId,
          '⏸️ *Promemoria disattivati*\n\nNon riceverai più notifiche per la firma iOS. Puoi riattivarli in qualsiasi momento generando un nuovo codice dall\'app.'
        );
      }
    } else {
      if (c.env.TELEGRAM_TOKEN) {
        await sendTelegramMessage(
          c.env.TELEGRAM_TOKEN,
          chatId,
          'Nessun account AILA risulta attualmente collegato a questa chat.'
        );
      }
    }
    return c.json({ ok: true });
  }

  // Estrazione codice da "/start <code>" o da testo semplice "<code>"
  let codeInput = '';
  if (text.toLowerCase().startsWith('/start')) {
    codeInput = text.slice(6).trim();
  } else if (!text.startsWith('/')) {
    codeInput = text;
  }

  if (!codeInput) {
    if (c.env.TELEGRAM_TOKEN) {
      await sendTelegramMessage(
        c.env.TELEGRAM_TOKEN,
        chatId,
        '👋 *Benvenuto nel Bot Promemoria AILA*\n\n' +
        'Per collegare il tuo account, genera un codice monouso dall\'app AILA (Profilo / Impostazioni) e invialo qui.'
      );
    }
    return c.json({ ok: true });
  }

  const codeUpper = codeInput.toUpperCase();
  const nowSeconds = Math.floor(Date.now() / 1000);

  const row = await c.env.DB.prepare(
    'SELECT code, user_id, expires_at, used_at FROM telegram_link_codes WHERE code = ?'
  ).bind(codeUpper).first<{ code: string; user_id: string; expires_at: number; used_at: string | null }>()
    .catch(() => null);

  if (!row || row.used_at !== null) {
    if (c.env.TELEGRAM_TOKEN) {
      await sendTelegramMessage(
        c.env.TELEGRAM_TOKEN,
        chatId,
        '❌ Codice non valido o già utilizzato. Genera un nuovo codice dall\'app AILA.'
      );
    }
    return c.json({ ok: true });
  }

  if (row.expires_at < nowSeconds) {
    if (c.env.TELEGRAM_TOKEN) {
      await sendTelegramMessage(
        c.env.TELEGRAM_TOKEN,
        chatId,
        '❌ Codice scaduto. Genera un nuovo codice dall\'app AILA.'
      );
    }
    return c.json({ ok: true });
  }

  // Codice valido! Collega l'account all'utente
  try {
    await c.env.DB.batch([
      c.env.DB.prepare('UPDATE telegram_link_codes SET used_at = CURRENT_TIMESTAMP WHERE code = ?').bind(codeUpper),
      c.env.DB.prepare('UPDATE telegram_links SET active = 0 WHERE chat_id = ?').bind(chatId),
      c.env.DB.prepare(
        `INSERT INTO telegram_links (user_id, chat_id, linked_at, active)
         VALUES (?, ?, CURRENT_TIMESTAMP, 1)
         ON CONFLICT(user_id) DO UPDATE SET chat_id = excluded.chat_id, linked_at = CURRENT_TIMESTAMP, active = 1`
      ).bind(row.user_id, chatId),
    ]);

    if (c.env.TELEGRAM_TOKEN) {
      await sendTelegramMessage(
        c.env.TELEGRAM_TOKEN,
        chatId,
        '✅ *Account AILA collegato con successo!*\n\n' +
        'Riceverai un promemoria qui su Telegram quando la tua firma iOS SideStore sta per scadere.'
      );
    }
  } catch (err) {
    console.error('[Telegram Webhook Error]', err);
  }

  return c.json({ ok: true });
});

export default telegramRoutes;
