// =============================================================================
// AILA — Passkey / WebAuthn Routes (/api/auth/passkeys/*)
// Registrazione e accesso con Face ID / Touch ID / Windows Hello (senza password).
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, UserRole } from '../types';
import { authMiddleware, issueToken } from '../auth';
import { nameCase } from '../names';
import { clientIp, isRateLimited, recordAttempt } from '../rateLimit';
import {
  PASSKEY_SUPPORTED_ALGORITHMS,
  base64UrlDecode,
  base64UrlEncode,
  randomChallenge,
  resolvePasskeyOriginAndRpId,
  validateSpkiPublicKey,
  verifyAuthenticatorData,
  verifyClientData,
  verifyPasskeySignature,
} from '../services/passkeys';

const passkeys = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

const CHALLENGE_TTL_SECONDS = 5 * 60;
const LOGIN_WINDOW = 15 * 60;
const LOGIN_MAX_FAILURES_PER_IP = 40;
const MAX_PASSKEYS_PER_USER = 10;

async function pruneExpiredChallenges(env: Env): Promise<void> {
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare('DELETE FROM passkey_challenges WHERE expires_at <= ?').bind(now).run().catch(() => {});
}

// ---------------------------------------------------------------------------
// GET /api/auth/passkeys — Elenco passkey dell'utente autenticato
// ---------------------------------------------------------------------------
passkeys.get('/', authMiddleware(), async (c) => {
  const { sub } = c.get('jwtPayload');
  const rows = await c.env.DB.prepare(
    `SELECT credential_id, label, created_at, last_used_at
     FROM user_passkeys
     WHERE user_id = ?
     ORDER BY created_at DESC`
  ).bind(sub).all<{
    credential_id: string;
    label: string | null;
    created_at: string;
    last_used_at: string | null;
  }>();

  return c.json({
    passkeys: rows.results.map((r) => ({
      credentialId: r.credential_id,
      label: r.label || 'Dispositivo',
      createdAt: r.created_at,
      lastUsedAt: r.last_used_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// DELETE /api/auth/passkeys/:credentialId — Rimuove una propria passkey
// ---------------------------------------------------------------------------
passkeys.delete('/:credentialId', authMiddleware(), async (c) => {
  const { sub } = c.get('jwtPayload');
  const credentialId = c.req.param('credentialId');
  const res = await c.env.DB.prepare(
    'DELETE FROM user_passkeys WHERE credential_id = ? AND user_id = ?'
  ).bind(credentialId, sub).run();
  if (!res.meta.changes) {
    return c.json({ error: 'Passkey non trovata' }, 404);
  }
  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/auth/passkeys/register/options — Avvia registrazione passkey
// ---------------------------------------------------------------------------
passkeys.post('/register/options', authMiddleware(), async (c) => {
  const originInfo = resolvePasskeyOriginAndRpId(c.req.header('Origin'), c.env);
  if (!originInfo) {
    return c.json({ error: 'Origine non autorizzata per le Passkey' }, 403);
  }

  const { sub } = c.get('jwtPayload');
  const user = await c.env.DB.prepare(
    'SELECT id, username, first_name, last_name FROM users WHERE id = ?'
  ).bind(sub).first<{ id: string; username: string; first_name: string; last_name: string }>();
  if (!user) return c.json({ error: 'Utente non trovato' }, 404);

  const existing = await c.env.DB.prepare(
    'SELECT credential_id FROM user_passkeys WHERE user_id = ?'
  ).bind(sub).all<{ credential_id: string }>();
  if (existing.results.length >= MAX_PASSKEYS_PER_USER) {
    return c.json({ error: `Puoi salvare al massimo ${MAX_PASSKEYS_PER_USER} Passkey per account` }, 400);
  }

  await pruneExpiredChallenges(c.env);
  const challenge = randomChallenge();
  const expiresAt = Math.floor(Date.now() / 1000) + CHALLENGE_TTL_SECONDS;
  await c.env.DB.prepare(
    `INSERT INTO passkey_challenges (challenge, user_id, purpose, rp_id, expires_at)
     VALUES (?, ?, 'register', ?, ?)`
  ).bind(challenge, user.id, originInfo.rpId, expiresAt).run();

  const userIdHandle = base64UrlEncode(new TextEncoder().encode(user.id));
  return c.json({
    challenge,
    rpId: originInfo.rpId,
    rpName: 'AILA',
    userId: userIdHandle,
    username: user.username,
    displayName: `${nameCase(user.first_name)} ${nameCase(user.last_name)}`.trim() || user.username,
    excludeCredentialIds: existing.results.map((r) => r.credential_id),
  });
});

// ---------------------------------------------------------------------------
// POST /api/auth/passkeys/register/verify — Conferma e salva passkey
// ---------------------------------------------------------------------------
passkeys.post('/register/verify', authMiddleware(), async (c) => {
  const originInfo = resolvePasskeyOriginAndRpId(c.req.header('Origin'), c.env);
  if (!originInfo) {
    return c.json({ error: 'Origine non autorizzata per le Passkey' }, 403);
  }

  const { sub } = c.get('jwtPayload');
  const body = await c.req.json<{
    credentialId?: string;
    clientDataJSON?: string;
    authenticatorData?: string;
    publicKeySpki?: string;
    algorithm?: number;
    label?: string;
  }>();

  const credentialId = typeof body.credentialId === 'string' ? body.credentialId.trim() : '';
  const clientDataB64 = typeof body.clientDataJSON === 'string' ? body.clientDataJSON.trim() : '';
  const authDataB64 = typeof body.authenticatorData === 'string' ? body.authenticatorData.trim() : '';
  const publicKeySpki = typeof body.publicKeySpki === 'string' ? body.publicKeySpki.trim() : '';
  const algorithm = typeof body.algorithm === 'number' ? body.algorithm : 0;
  const label = typeof body.label === 'string' && body.label.trim() ? body.label.trim().slice(0, 60) : 'Dispositivo';

  if (!credentialId || credentialId.length > 512 || !clientDataB64 || !authDataB64 || !publicKeySpki) {
    return c.json({ error: 'Dati Passkey incompleti' }, 400);
  }
  if (!PASSKEY_SUPPORTED_ALGORITHMS.has(algorithm) || !(await validateSpkiPublicKey(publicKeySpki, algorithm))) {
    return c.json({ error: 'Chiave Passkey non supportata' }, 400);
  }

  let clientDataBytes: Uint8Array;
  let authDataBytes: Uint8Array;
  try {
    clientDataBytes = base64UrlDecode(clientDataB64);
    authDataBytes = base64UrlDecode(authDataB64);
  } catch {
    return c.json({ error: 'Codifica Passkey non valida' }, 400);
  }

  let challengeFromClient = '';
  try {
    const parsed = JSON.parse(new TextDecoder().decode(clientDataBytes)) as { challenge?: unknown };
    if (typeof parsed.challenge === 'string') challengeFromClient = parsed.challenge;
  } catch {
    return c.json({ error: 'clientDataJSON non valido' }, 400);
  }

  const now = Math.floor(Date.now() / 1000);
  const storedChallenge = await c.env.DB.prepare(
    `SELECT challenge, user_id, rp_id, expires_at
     FROM passkey_challenges
     WHERE challenge = ? AND purpose = 'register'`
  ).bind(challengeFromClient).first<{ challenge: string; user_id: string | null; rp_id: string; expires_at: number }>();

  if (!storedChallenge || storedChallenge.expires_at <= now || storedChallenge.user_id !== sub) {
    return c.json({ error: 'Sfida Passkey scaduta o non valida: riprova' }, 400);
  }
  // Consuma subito la sfida (monouso).
  await c.env.DB.prepare('DELETE FROM passkey_challenges WHERE challenge = ?').bind(storedChallenge.challenge).run();

  if (!verifyClientData(clientDataBytes, 'webauthn.create', storedChallenge.challenge, originInfo.origin)) {
    return c.json({ error: 'Verifica clientData Passkey fallita' }, 400);
  }
  const authCheck = await verifyAuthenticatorData(authDataBytes, storedChallenge.rp_id);
  if (!authCheck.ok) {
    return c.json({ error: 'Verifica autenticatore Passkey fallita' }, 400);
  }

  try {
    await c.env.DB.prepare(
      `INSERT INTO user_passkeys (credential_id, user_id, public_key_spki, algorithm, sign_count, label)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(credentialId, sub, publicKeySpki, algorithm, authCheck.signCount, label).run();
  } catch {
    return c.json({ error: 'Questa Passkey è già registrata' }, 409);
  }

  return c.json({
    credentialId,
    label,
    createdAt: new Date().toISOString(),
    lastUsedAt: null,
  }, 201);
});

// ---------------------------------------------------------------------------
// POST /api/auth/passkeys/login/options — Avvia accesso con Passkey (anche senza username)
// ---------------------------------------------------------------------------
passkeys.post('/login/options', async (c) => {
  const originInfo = resolvePasskeyOriginAndRpId(c.req.header('Origin'), c.env);
  if (!originInfo) {
    return c.json({ error: 'Origine non autorizzata per le Passkey' }, 403);
  }

  const body = await c.req.json<{ username?: string }>().catch(() => ({} as { username?: string }));
  const username = typeof body.username === 'string' ? body.username.trim().toLowerCase() : '';

  let allowCredentialIds: string[] = [];
  if (username) {
    const rows = await c.env.DB.prepare(
      `SELECT p.credential_id
       FROM user_passkeys p
       JOIN users u ON u.id = p.user_id
       WHERE u.username = ?`
    ).bind(username).all<{ credential_id: string }>();
    allowCredentialIds = rows.results.map((r) => r.credential_id);
  }

  await pruneExpiredChallenges(c.env);
  const challenge = randomChallenge();
  const expiresAt = Math.floor(Date.now() / 1000) + CHALLENGE_TTL_SECONDS;
  await c.env.DB.prepare(
    `INSERT INTO passkey_challenges (challenge, user_id, purpose, rp_id, expires_at)
     VALUES (?, NULL, 'login', ?, ?)`
  ).bind(challenge, originInfo.rpId, expiresAt).run();

  return c.json({
    challenge,
    rpId: originInfo.rpId,
    allowCredentialIds,
  });
});

// ---------------------------------------------------------------------------
// POST /api/auth/passkeys/login/verify — Verifica firma Passkey ed emette JWT
// ---------------------------------------------------------------------------
passkeys.post('/login/verify', async (c) => {
  const originInfo = resolvePasskeyOriginAndRpId(c.req.header('Origin'), c.env);
  if (!originInfo) {
    return c.json({ error: 'Origine non autorizzata per le Passkey' }, 403);
  }

  const ip = clientIp(c.req);
  const ipKey = ip ? `passkey:ip:${ip}` : null;
  if (ipKey && (await isRateLimited(c.env, ipKey, LOGIN_MAX_FAILURES_PER_IP, LOGIN_WINDOW))) {
    return c.json({ error: 'Troppi tentativi: riprova fra qualche minuto' }, 429);
  }
  const fail = async (msg = 'Accesso con Passkey non riuscito', status: 400 | 401 = 401) => {
    if (ipKey) await recordAttempt(c.env, ipKey, LOGIN_WINDOW);
    return c.json({ error: msg }, status);
  };

  const body = await c.req.json<{
    credentialId?: string;
    clientDataJSON?: string;
    authenticatorData?: string;
    signature?: string;
  }>();

  const credentialId = typeof body.credentialId === 'string' ? body.credentialId.trim() : '';
  const clientDataB64 = typeof body.clientDataJSON === 'string' ? body.clientDataJSON.trim() : '';
  const authDataB64 = typeof body.authenticatorData === 'string' ? body.authenticatorData.trim() : '';
  const signatureB64 = typeof body.signature === 'string' ? body.signature.trim() : '';
  if (!credentialId || !clientDataB64 || !authDataB64 || !signatureB64) {
    return fail('Dati Passkey incompleti', 400);
  }

  let clientDataBytes: Uint8Array;
  let authDataBytes: Uint8Array;
  let signatureBytes: Uint8Array;
  try {
    clientDataBytes = base64UrlDecode(clientDataB64);
    authDataBytes = base64UrlDecode(authDataB64);
    signatureBytes = base64UrlDecode(signatureB64);
  } catch {
    return fail('Codifica Passkey non valida', 400);
  }

  let challengeFromClient = '';
  try {
    const parsed = JSON.parse(new TextDecoder().decode(clientDataBytes)) as { challenge?: unknown };
    if (typeof parsed.challenge === 'string') challengeFromClient = parsed.challenge;
  } catch {
    return fail('clientDataJSON non valido', 400);
  }

  const now = Math.floor(Date.now() / 1000);
  const storedChallenge = await c.env.DB.prepare(
    `SELECT challenge, rp_id, expires_at
     FROM passkey_challenges
     WHERE challenge = ? AND purpose = 'login'`
  ).bind(challengeFromClient).first<{ challenge: string; rp_id: string; expires_at: number }>();

  if (!storedChallenge || storedChallenge.expires_at <= now) {
    return fail('Sfida Passkey scaduta: riprova', 401);
  }
  await c.env.DB.prepare('DELETE FROM passkey_challenges WHERE challenge = ?').bind(storedChallenge.challenge).run();

  if (!verifyClientData(clientDataBytes, 'webauthn.get', storedChallenge.challenge, originInfo.origin)) {
    return fail();
  }
  const authCheck = await verifyAuthenticatorData(authDataBytes, storedChallenge.rp_id);
  if (!authCheck.ok) {
    return fail();
  }

  const row = await c.env.DB.prepare(
    `SELECT p.credential_id, p.public_key_spki, p.algorithm, p.sign_count,
            u.id, u.first_name, u.last_name, u.username, u.password_hash, u.role,
            u.class_id, cl.label AS class_label,
            sp.height_cm, sp.priority_pass, sp.notification_board_enabled
     FROM user_passkeys p
     JOIN users u ON u.id = p.user_id
     LEFT JOIN classes cl ON cl.id = u.class_id
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE p.credential_id = ?`
  ).bind(credentialId).first<{
    credential_id: string;
    public_key_spki: string;
    algorithm: number;
    sign_count: number;
    id: string;
    first_name: string;
    last_name: string;
    username: string;
    password_hash: string;
    role: string;
    class_id: string | null;
    class_label: string | null;
    height_cm: number | null;
    priority_pass: number | null;
    notification_board_enabled: number | null;
  }>();

  if (!row) {
    return fail('Passkey non riconosciuta su AILA', 401);
  }

  const validSig = await verifyPasskeySignature(
    row.public_key_spki,
    row.algorithm,
    authDataBytes,
    clientDataBytes,
    signatureBytes
  );
  if (!validSig) {
    return fail();
  }

  // Controllo anti-clonazione WebAuthn: se l'autenticatore incrementa il contatore, deve crescere.
  if (authCheck.signCount > 0 && row.sign_count > 0 && authCheck.signCount <= row.sign_count) {
    return fail('Passkey non coerente: accedi con password e rigenerala', 401);
  }

  await c.env.DB.prepare(
    `UPDATE user_passkeys
     SET sign_count = ?, last_used_at = CURRENT_TIMESTAMP
     WHERE credential_id = ?`
  ).bind(Math.max(row.sign_count, authCheck.signCount), row.credential_id).run();

  const classId = row.class_id || 'DEFAULT_CLASS';
  const token = await issueToken(c.env, {
    id: row.id,
    username: row.username,
    role: row.role as UserRole,
    classId,
    passwordHash: row.password_hash,
  });

  return c.json({
    success: true,
    token,
    user: {
      id: row.id,
      firstName: nameCase(row.first_name),
      lastName: nameCase(row.last_name),
      username: row.username,
      role: row.role,
      classId,
      classLabel: row.class_label,
      heightCm: row.height_cm,
      priorityPass: Boolean(row.priority_pass),
      notificationBoardEnabled: Boolean(row.notification_board_enabled ?? 1),
    },
  });
});

export default passkeys;
