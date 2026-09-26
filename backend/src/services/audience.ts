// =============================================================================
// AILA — Destinatari dei sondaggi
// =============================================================================
//
// Un sondaggio (a ordinamento o interrogazioni) può essere rivolto a tutta la classe
// (`audience_json` NULL) o solo ad alcune persone scelte dal Rappresentante. Chi non è fra i
// destinatari non lo vede e non può rispondere; il Rappresentante li vede sempre tutti per
// poterli gestire. I conteggi "hanno risposto X di Y" usano i destinatari, non la classe intera.

import type { Env } from '../types';

/** Membri della classe che possono rispondere ai sondaggi (studenti e Rappresentante). */
export async function classMemberIds(env: Env, classId: string): Promise<string[]> {
  const rows = await env.DB.prepare(
    "SELECT id FROM users WHERE class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')"
  ).bind(classId).all<{ id: string }>();
  return rows.results.map((r) => r.id);
}

/** `null` = tutta la classe. Un JSON rovinato vale come "tutta la classe", mai come "nessuno". */
export function parseAudience(json: string | null | undefined): string[] | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) return null;
    const ids = parsed.filter((v): v is string => typeof v === 'string');
    return ids.length > 0 ? ids : null;
  } catch {
    return null;
  }
}

/** Destinatari effettivi: quelli scelti ancora iscritti alla classe, oppure tutta la classe. */
export function effectiveAudience(audience: string[] | null, members: string[]): string[] {
  if (!audience) return members;
  const set = new Set(members);
  return audience.filter((id) => set.has(id));
}

export function isInAudience(audience: string[] | null, userId: string): boolean {
  return audience === null || audience.includes(userId);
}

/**
 * Valida l'elenco destinatari arrivato dal client. Vuoto/assente = tutta la classe; se contiene
 * tutti i membri della classe si salva comunque come "tutta la classe", così chi si iscrive dopo
 * è incluso.
 */
export function normalizeAudience(
  raw: unknown,
  members: string[]
): { ok: true; audience: string[] | null } | { ok: false; error: string } {
  if (raw === undefined || raw === null) return { ok: true, audience: null };
  if (!Array.isArray(raw)) return { ok: false, error: 'Destinatari non validi' };
  const ids = [...new Set(raw.filter((v): v is string => typeof v === 'string'))];
  if (ids.length === 0) return { ok: true, audience: null };
  const memberSet = new Set(members);
  if (ids.some((id) => !memberSet.has(id))) {
    return { ok: false, error: 'Alcuni destinatari non fanno parte della classe' };
  }
  if (ids.length >= memberSet.size) return { ok: true, audience: null };
  return { ok: true, audience: ids };
}
