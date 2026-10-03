// =============================================================================
// AILA — Codici Rappresentante per classe e monouso
// =============================================================================
//
// Per registrarsi come Rappresentante serve un codice emesso per quella classe (tabella
// representative_invites, migrazione 017), che vale una volta sola e scade. Li emette chi ha
// ADMIN_SECRET (routes/admin.ts), non un Rappresentante: con un codice per la propria classe una
// persona sola potrebbe registrare il secondo Rappresentante e, nominando la Guardia, avere le tre
// firme del quorum che svela gli anonimi.

import { newUUID, randomCode, sha256Hex, timingSafeEqual } from '../auth';
import { italianToday } from './summarizer';
import type { Env } from '../types';

export const INVITE_CODE_LENGTH = 10;
export const INVITE_DEFAULT_TTL_DAYS = 7;
export const INVITE_MAX_TTL_DAYS = 30;
// Al massimo un codice per ogni posto da Rappresentante della classe.
export const INVITE_MAX_COUNT = 2;

/** Maiuscole, senza spazi e trattini: "abcde-fghij" e "ABCDEFGHIJ" sono lo stesso codice. */
export function normalizeInviteCode(code: string): string {
  return code.trim().toUpperCase().replace(/[\s-]/g, '');
}

/** "ABCDEFGHIJ" → "ABCDE-FGHIJ", piu' facile da dettare. */
export function formatInviteCode(code: string): string {
  return `${code.slice(0, 5)}-${code.slice(5)}`;
}

/**
 * [count] codici nuovi e diversi per la classe, uno per ogni Rappresentante (mai un codice che vale
 * due volte: chi lo riceve potrebbe usarlo per due account suoi). Si salva solo l'hash, il codice
 * in chiaro lo vede solo chi lo emette.
 */
export async function issueRepresentativeInvites(
  env: Env,
  classId: string,
  ttlDays: number,
  count: number
): Promise<{ codes: { id: string; code: string }[]; expiresAt: number }> {
  const expiresAt = Math.floor(Date.now() / 1000) + ttlDays * 24 * 60 * 60;
  const codes: { id: string; code: string }[] = [];
  const inserts: D1PreparedStatement[] = [];
  for (let i = 0; i < count; i++) {
    const code = randomCode(INVITE_CODE_LENGTH);
    const id = newUUID();
    codes.push({ id, code: formatInviteCode(code) });
    inserts.push(
      env.DB.prepare('INSERT INTO representative_invites (id, class_id, code_hash, expires_at) VALUES (?, ?, ?, ?)')
        .bind(id, classId, await sha256Hex(code), expiresAt)
    );
  }
  await env.DB.batch(inserts);
  return { codes, expiresAt };
}

export type InviteCheck =
  | { ok: true; id: string }
  | { ok: false; status: 400 | 503; error: string };

/**
 * Il codice vale per [classId]? Non lo segna come usato: lo fa [claimRepresentativeInvite] quando
 * tutti gli altri controlli della registrazione sono passati.
 */
export async function checkRepresentativeInvite(env: Env, code: string, classId: string): Promise<InviteCheck> {
  let row: { id: string; class_id: string; expires_at: number; used_by: string | null } | null;
  try {
    row = await env.DB.prepare(
      'SELECT id, class_id, expires_at, used_by FROM representative_invites WHERE code_hash = ?'
    ).bind(await sha256Hex(normalizeInviteCode(code))).first();
  } catch {
    return { ok: false, status: 503, error: 'Codici Rappresentante non ancora attivi sul server (manca la migrazione 017)' };
  }
  if (!row) return { ok: false, status: 400, error: 'Codice Rappresentante non valido' };
  if (row.class_id !== classId) {
    return { ok: false, status: 400, error: "Questo codice Rappresentante è di un'altra classe: controlla la classe scelta" };
  }
  if (row.used_by) return { ok: false, status: 400, error: 'Codice Rappresentante già usato: ne serve uno nuovo' };
  if (row.expires_at <= Math.floor(Date.now() / 1000)) {
    return { ok: false, status: 400, error: 'Codice Rappresentante scaduto: ne serve uno nuovo' };
  }
  return { ok: true, id: row.id };
}

/**
 * Segna il codice come usato da [userId]. `false` se nel frattempo l'ha preso un'altra
 * registrazione: l'UPDATE con `used_by IS NULL` lo assegna a una sola.
 */
export async function claimRepresentativeInvite(env: Env, inviteId: string, userId: string): Promise<boolean> {
  const res = await env.DB.prepare(
    `UPDATE representative_invites SET used_by = ?, used_at = ?
     WHERE id = ? AND used_by IS NULL AND expires_at > ?`
  ).bind(userId, Math.floor(Date.now() / 1000), inviteId, Math.floor(Date.now() / 1000)).run();
  return (res.meta?.changes ?? 0) === 1;
}

/** Restituisce il codice se la registrazione e' fallita dopo averlo preso. */
export async function releaseRepresentativeInvite(env: Env, inviteId: string, userId: string): Promise<void> {
  await env.DB.prepare('UPDATE representative_invites SET used_by = NULL, used_at = NULL WHERE id = ? AND used_by = ?')
    .bind(inviteId, userId).run();
}

/**
 * Codice globale di transizione: vale solo se REPRESENTATIVE_SIGNUP_CODE e
 * REPRESENTATIVE_GLOBAL_CODE_UNTIL sono impostati e oggi (ora italiana) non e' dopo quella data.
 */
export function matchesGlobalCode(env: Env, code: string, now: Date = new Date()): boolean {
  const until = env.REPRESENTATIVE_GLOBAL_CODE_UNTIL?.trim();
  if (!env.REPRESENTATIVE_SIGNUP_CODE || !until || !/^\d{4}-\d{2}-\d{2}$/.test(until)) return false;
  if (italianToday(now) > until) return false;
  return timingSafeEqual(code, env.REPRESENTATIVE_SIGNUP_CODE);
}
