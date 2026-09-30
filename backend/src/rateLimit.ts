// =============================================================================
// AILA — Limite ai tentativi (login, registrazione, reset password)
// =============================================================================
//
// Su workers.dev non si possono mettere regole WAF, quindi il conteggio sta in D1 (tabella
// auth_attempts, migrazione 013): una riga per chiave ("login:user:mario", "login:ip:1.2.3.4"),
// con il numero di tentativi dall'inizio della finestra. Se la tabella non c'e' ancora i
// controlli si saltano: meglio un login senza limite che un login che non funziona.

import type { Env } from './types';

const nowSeconds = () => Math.floor(Date.now() / 1000);

/** Indirizzo del client come lo vede Cloudflare (vuoto fuori da Cloudflare, es. nei test). */
export function clientIp(req: { header(name: string): string | undefined }): string {
  return (req.header('CF-Connecting-IP') ?? '').trim();
}

/** True se [key] ha gia' raggiunto [limit] tentativi nella finestra in corso. */
export async function isRateLimited(env: Env, key: string, limit: number, windowSeconds: number): Promise<boolean> {
  try {
    const row = await env.DB.prepare('SELECT count, window_start FROM auth_attempts WHERE key = ?')
      .bind(key)
      .first<{ count: number; window_start: number }>();
    if (!row) return false;
    if (row.window_start <= nowSeconds() - windowSeconds) return false;
    return row.count >= limit;
  } catch {
    return false;
  }
}

/** Conta un tentativo per [key]; una finestra scaduta riparte da uno. */
export async function recordAttempt(env: Env, key: string, windowSeconds: number): Promise<void> {
  const now = nowSeconds();
  try {
    await env.DB.prepare(
      `INSERT INTO auth_attempts (key, count, window_start) VALUES (?1, 1, ?2)
       ON CONFLICT(key) DO UPDATE SET
         count = CASE WHEN auth_attempts.window_start <= ?3 THEN 1 ELSE auth_attempts.count + 1 END,
         window_start = CASE WHEN auth_attempts.window_start <= ?3 THEN ?2 ELSE auth_attempts.window_start END`
    ).bind(key, now, now - windowSeconds).run();
  } catch {
    // Tabella assente (migrazione 013 non applicata): nessun limite.
  }
}

export async function clearAttempts(env: Env, key: string): Promise<void> {
  try {
    await env.DB.prepare('DELETE FROM auth_attempts WHERE key = ?').bind(key).run();
  } catch {
    // Idem.
  }
}

/** Dal cron: via le righe ferme da piu' di un giorno. */
export async function pruneAttempts(env: Env): Promise<void> {
  try {
    await env.DB.prepare('DELETE FROM auth_attempts WHERE window_start < ?').bind(nowSeconds() - 86_400).run();
  } catch {
    // Idem.
  }
}
