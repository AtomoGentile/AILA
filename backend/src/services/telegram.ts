// =============================================================================
// CIRCOLARE+ — Telegram Service & Cron Reminders
// =============================================================================

import type { Env } from '../types';

/**
 * Invia un messaggio a una chat Telegram via Telegram Bot API.
 */
export async function sendTelegramMessage(token: string, chatId: string | number, text: string): Promise<boolean> {
  if (!token) return false;
  try {
    const res = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text,
        parse_mode: 'Markdown',
      }),
    });
    if (!res.ok) {
      console.error(`[Telegram] Non inviato a ${chatId}: status ${res.status}`);
      return false;
    }
    return true;
  } catch (err) {
    console.error(`[Telegram] Errore nell'invio del messaggio a ${chatId}:`, err);
    return false;
  }
}

/**
 * Controlla le scadenze della firma iOS e invia promemoria Telegram agli utenti
 * la cui firma scade entro le successive 36 ore.
 */
export async function checkTelegramSignatureReminders(env: Env): Promise<void> {
  if (!env.TELEGRAM_TOKEN) return;

  const now = new Date();
  const nowIso = now.toISOString();
  const horizonMs = now.getTime() + 36 * 3600 * 1000;
  const horizonIso = new Date(horizonMs).toISOString();

  try {
    const rows = await env.DB.prepare(
      `SELECT u.id AS user_id, u.signature_expires_at, tl.chat_id
       FROM users u
       JOIN telegram_links tl ON tl.user_id = u.id
       WHERE tl.active = 1
         AND u.signature_expires_at IS NOT NULL
         AND u.signature_expires_at > ?
         AND u.signature_expires_at <= ?
         AND NOT EXISTS (
           SELECT 1 FROM telegram_signature_reminders tsr
           WHERE tsr.user_id = u.id AND tsr.signature_expires_at = u.signature_expires_at
         )`
    ).bind(nowIso, horizonIso).all<{ user_id: string; signature_expires_at: string; chat_id: string }>();

    if (!rows.results || rows.results.length === 0) return;

    for (const candidate of rows.results) {
      try {
        // Inserisci prima nella tabella dei promemoria inviati: la chiave primaria composta
        // (user_id, signature_expires_at) blocca invii duplicati anche in esecuzioni concorrenti.
        await env.DB.prepare(
          `INSERT INTO telegram_signature_reminders (user_id, signature_expires_at, sent_at)
           VALUES (?, ?, CURRENT_TIMESTAMP)`
        ).bind(candidate.user_id, candidate.signature_expires_at).run();

        const messageText =
          `⚠️ *AILA — Firma iOS in scadenza*\n\n` +
          `La firma della tua app sta per scadere (entro 36 ore). ` +
          `Apri SideStore per rinnovarla e continuare a usare l'app senza interruzioni.`;

        await sendTelegramMessage(env.TELEGRAM_TOKEN, candidate.chat_id, messageText);
      } catch (err) {
        // Se l'inserimento o l'invio fallisce per questo utente, continua con i prossimi.
        console.error(`[Telegram Cron] Impossibile inviare promemoria a ${candidate.user_id}:`, err);
      }
    }
  } catch (err) {
    console.error('[Telegram Cron] Errore durante il controllo delle scadenze:', err);
  }
}
