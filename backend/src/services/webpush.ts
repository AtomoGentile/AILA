// =============================================================================
// AILA — Web Push per la PWA (RFC 8030 + cifratura aes128gcm RFC 8291 + VAPID RFC 8292)
//
// La cifratura e la firma VAPID le fa @block65/webcrypto-web-push: usa solo WebCrypto (gira nel
// Worker senza polyfill Node), invia aes128gcm e lo schema "vapid" accettati anche da Apple.
//
// Secret (wrangler secret put <NOME>): VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT.
// Senza, il web push è spento e le notifiche native continuano come prima.
// =============================================================================

import { buildPushPayload, type PushSubscription, type VapidKeys } from '@block65/webcrypto-web-push';
import type { Env } from '../types';

export interface WebPushMessage {
  title: string;
  body: string;
  data?: Record<string, string>;
  // Categoria delle Impostazioni ("circulars", "board", ...), vedi notificationKind in fcm.ts.
  kind: string;
}

interface WebPushRow {
  endpoint: string;
  p256dh: string;
  auth: string;
  muted_kinds: string;
}

// Tetti di tempo: un push service lento non deve trattenere FCM, la rotta o il cron.
const REQUEST_TIMEOUT_MS = 5_000;
const OVERALL_TIMEOUT_MS = 8_000;
// Il payload cifrato ha un massimo di ~4 KB: il testo si accorcia prima.
const MAX_BODY_CHARS = 1_000;

export function vapidKeysOf(env: Env): VapidKeys | null {
  if (!env.VAPID_PUBLIC_KEY || !env.VAPID_PRIVATE_KEY || !env.VAPID_SUBJECT) return null;
  return {
    subject: env.VAPID_SUBJECT.trim(),
    publicKey: env.VAPID_PUBLIC_KEY.trim(),
    privateKey: env.VAPID_PRIVATE_KEY.trim(),
  };
}

export function isMuted(mutedKinds: string | null | undefined, kind: string): boolean {
  if (!kind || !mutedKinds) return false;
  return mutedKinds.split(',').map((k) => k.trim()).includes(kind);
}

async function queryWebRecipients(env: Env, where: string, binds: unknown[]): Promise<WebPushRow[]> {
  try {
    const rows = await env.DB.prepare(
      `SELECT DISTINCT w.endpoint, w.p256dh, w.auth, w.muted_kinds FROM web_push_subscriptions w ${where}`
    ).bind(...binds).all<WebPushRow>();
    return rows.results;
  } catch (e) {
    // Migrazione 010 non ancora applicata: nessun iscritto web.
    console.error('[WebPush] Lettura iscrizioni fallita (manca la migrazione 010?)', e);
    return [];
  }
}

async function sendOne(env: Env, vapid: VapidKeys, row: WebPushRow, message: WebPushMessage): Promise<void> {
  const subscription: PushSubscription = {
    endpoint: row.endpoint,
    expirationTime: null,
    keys: { p256dh: row.p256dh, auth: row.auth },
  };
  const payload = await buildPushPayload(
    {
      data: {
        title: message.title,
        body: message.body.slice(0, MAX_BODY_CHARS),
        kind: message.kind,
        data: message.data ?? {},
      },
      options: { ttl: 60 * 60 * 24, urgency: 'normal' },
    },
    subscription,
    vapid
  );

  const res = await fetch(row.endpoint, { ...payload, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
  if (res.status === 404 || res.status === 410) {
    // Iscrizione scaduta o revocata dal browser: si cancella.
    await env.DB.prepare('DELETE FROM web_push_subscriptions WHERE endpoint = ?').bind(row.endpoint).run();
  } else if (!res.ok) {
    console.error(`[WebPush] ${res.status} da ${new URL(row.endpoint).host}: ${(await res.text()).slice(0, 200)}`);
  }
}

/**
 * Invia a tutte le iscrizioni che corrispondono a `where` (alias tabella `w`), saltando chi ha
 * silenziato la categoria. Non lancia mai e termina entro OVERALL_TIMEOUT_MS.
 */
export async function sendWebPush(env: Env, where: string, binds: unknown[], message: WebPushMessage): Promise<void> {
  try {
    const vapid = vapidKeysOf(env);
    if (!vapid) return;

    const rows = (await queryWebRecipients(env, where, binds)).filter((r) => !isMuted(r.muted_kinds, message.kind));
    if (rows.length === 0) return;

    const all = Promise.allSettled(rows.map((row) => sendOne(env, vapid, row, message))).then((results) => {
      for (const r of results) {
        if (r.status === 'rejected') console.error('[WebPush] Invio fallito', r.reason);
      }
    });
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<void>((resolve) => {
      timer = setTimeout(() => {
        console.error('[WebPush] Tempo scaduto, invii rimanenti abbandonati');
        resolve();
      }, OVERALL_TIMEOUT_MS);
    });
    await Promise.race([all, timeout]);
    if (timer !== undefined) clearTimeout(timer);
  } catch (e) {
    console.error('[WebPush] Errore inatteso', e);
  }
}
