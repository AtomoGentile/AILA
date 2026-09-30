// =============================================================================
// AILA — Codice della classe
// =============================================================================
//
// Per registrarsi in una classe che ha gia' degli iscritti serve il suo codice (tabella
// class_invites, migrazione 013). Lo vede il Rappresentante nella Scheda Classe, lo gira ai
// compagni e puo' rigenerarlo se finisce in giro.

import { randomCode } from '../auth';
import type { Env } from '../types';

export const CLASS_CODE_LENGTH = 6;

/**
 * Il codice della classe, creato al primo bisogno se [createIfMissing]. `null` se non c'e' (o se
 * la migrazione 013 non e' ancora applicata): in quel caso la registrazione non lo chiede.
 */
export async function classInviteCode(
  env: Env,
  classId: string,
  { createIfMissing }: { createIfMissing: boolean }
): Promise<string | null> {
  try {
    const row = await env.DB.prepare('SELECT code FROM class_invites WHERE class_id = ?')
      .bind(classId)
      .first<{ code: string }>();
    if (row) return row.code;
    if (!createIfMissing) return null;
    const code = randomCode(CLASS_CODE_LENGTH);
    await env.DB.prepare('INSERT OR IGNORE INTO class_invites (class_id, code) VALUES (?, ?)').bind(classId, code).run();
    // Se due richieste l'hanno creato insieme vince la prima: si rilegge quello salvato.
    const saved = await env.DB.prepare('SELECT code FROM class_invites WHERE class_id = ?')
      .bind(classId)
      .first<{ code: string }>();
    return saved?.code ?? code;
  } catch {
    return null;
  }
}

/** Nuovo codice (quello vecchio smette di valere). `null` se la tabella non c'e'. */
export async function regenerateClassInviteCode(env: Env, classId: string): Promise<string | null> {
  const code = randomCode(CLASS_CODE_LENGTH);
  try {
    await env.DB.prepare(
      `INSERT INTO class_invites (class_id, code, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)
       ON CONFLICT(class_id) DO UPDATE SET code = excluded.code, created_at = excluded.created_at`
    ).bind(classId, code).run();
    return code;
  } catch {
    return null;
  }
}
