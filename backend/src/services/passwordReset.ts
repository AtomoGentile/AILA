import { randomCode, sha256Hex } from '../auth';
import type { Env } from '../types';

const RESET_CODE_TTL_SECONDS = 24 * 60 * 60;

export async function issuePasswordResetCode(env: Env, userId: string, createdBy: string | null) {
  const code = randomCode(8);
  const expiresAt = Math.floor(Date.now() / 1000) + RESET_CODE_TTL_SECONDS;
  await env.DB.prepare(
    `INSERT INTO password_reset_codes (user_id, code_hash, expires_at, created_by, created_at)
     VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(user_id) DO UPDATE SET code_hash = excluded.code_hash, expires_at = excluded.expires_at,
       created_by = excluded.created_by, created_at = excluded.created_at`
  ).bind(userId, await sha256Hex(code), expiresAt, createdBy).run();
  return { code, expiresAt: new Date(expiresAt * 1000).toISOString() };
}
