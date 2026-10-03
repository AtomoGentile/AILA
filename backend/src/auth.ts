// =============================================================================
// CIRCOLARE+ — Auth Helpers & JWT Middleware
// Uses native WebCrypto — no external dependencies
// =============================================================================

import type { Context, Next } from 'hono';
import type { Env, JWTPayload, UserRole } from './types';

// ---------------------------------------------------------------------------
// JWT — HS256 with WebCrypto
// ---------------------------------------------------------------------------

const base64url = {
  encode: (buf: ArrayBuffer | Uint8Array): string => {
    const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
    return btoa(String.fromCharCode(...bytes))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  },

  decode: (str: string): Uint8Array => {
    const b64 = str.replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(b64);
    const buf = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) buf[i] = raw.charCodeAt(i);
    return buf;
  },
};

async function getHmacKey(secret: string): Promise<CryptoKey> {
  const enc = new TextEncoder();
  return crypto.subtle.importKey(
    'raw',
    enc.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign', 'verify']
  );
}

export async function signJWT(payload: Omit<JWTPayload, 'iat' | 'exp'>, secret: string, expiresInSeconds = 60 * 60 * 24 * 30): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const fullPayload: JWTPayload = { ...payload, iat: now, exp: now + expiresInSeconds };

  const header = base64url.encode(new TextEncoder().encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' })));
  const body = base64url.encode(new TextEncoder().encode(JSON.stringify(fullPayload)));
  const data = `${header}.${body}`;

  const key = await getHmacKey(secret);
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));

  return `${data}.${base64url.encode(sig)}`;
}

export async function verifyJWT(token: string, secret: string): Promise<JWTPayload | null> {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const [header, body, signature] = parts;
    // Solo i token che emettiamo noi: HS256 e una scadenza. Un token senza `exp` varrebbe per
    // sempre (prima `undefined < adesso` era falso e passava).
    const head = JSON.parse(new TextDecoder().decode(base64url.decode(header))) as { alg?: unknown };
    if (head.alg !== 'HS256') return null;
    const key = await getHmacKey(secret);
    const valid = await crypto.subtle.verify(
      'HMAC',
      key,
      base64url.decode(signature),
      new TextEncoder().encode(`${header}.${body}`)
    );

    if (!valid) return null;

    const payload = JSON.parse(new TextDecoder().decode(base64url.decode(body))) as JWTPayload;
    if (typeof payload.exp !== 'number' || payload.exp < Math.floor(Date.now() / 1000)) return null;

    return payload;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Password Hashing — PBKDF2 with WebCrypto
// ---------------------------------------------------------------------------

export async function hashPassword(password: string, salt?: string): Promise<{ hash: string; salt: string }> {
  const usedSalt = salt ?? base64url.encode(crypto.getRandomValues(new Uint8Array(16)));
  const enc = new TextEncoder();

  const keyMaterial = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: enc.encode(usedSalt), iterations: 100_000, hash: 'SHA-256' },
    keyMaterial,
    256
  );

  return { hash: base64url.encode(bits), salt: usedSalt };
}

export async function verifyPassword(password: string, storedHash: string, salt: string): Promise<boolean> {
  const { hash } = await hashPassword(password, salt);
  return timingSafeEqual(hash, storedHash ?? '');
}

/** Confronto a tempo costante: `===` si ferma al primo carattere diverso. */
export function timingSafeEqual(a: string, b: string): boolean {
  let diff = a.length ^ b.length;
  const len = Math.max(a.length, b.length);
  for (let i = 0; i < len; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

/** Hash e sale da salvare nella colonna password_hash ("hash:salt"). */
export async function storedPasswordHash(password: string): Promise<string> {
  const { hash, salt } = await hashPassword(password);
  return `${hash}:${salt}`;
}

/**
 * "Versione" della password messa nel token (`pv`): cambia quando cambia la password, cosi' i
 * token emessi prima di un cambio o di un reset smettono di valere. E' un HMAC con il segreto
 * del server, non un pezzo dell'hash: dal token non si ricava niente per indovinare la password.
 */
export async function passwordVersion(storedHash: string, secret: string): Promise<string> {
  const key = await getHmacKey(secret);
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`pv:${storedHash}`));
  return base64url.encode(sig).slice(0, 16);
}

/** SHA-256 esadecimale (codici monouso: si salva solo l'hash). */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Codice leggibile da dettare o scrivere: niente 0/O, 1/I/L. */
export function randomCode(length: number): string {
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return [...bytes].map((b) => alphabet[b % alphabet.length]).join('');
}

/** Regole comuni per una password nuova (registrazione, cambio, reset). */
export function passwordProblem(password: unknown): string | null {
  if (typeof password !== 'string' || password.length < 8) return 'La password deve essere di almeno 8 caratteri';
  if (password.length > 128) return 'La password può avere al massimo 128 caratteri';
  return null;
}

// ---------------------------------------------------------------------------
// Hono Middleware — Auth
// ---------------------------------------------------------------------------

type AuthVariables = {
  jwtPayload: JWTPayload;
};

export function authMiddleware() {
  return async (c: Context<{ Bindings: Env; Variables: AuthVariables }>, next: Next) => {
    const authHeader = c.req.header('Authorization');
    if (!authHeader?.startsWith('Bearer ')) {
      return c.json({ error: 'Autenticazione richiesta' }, 401);
    }

    const token = authHeader.slice(7);
    const payload = await verifyJWT(token, c.env.JWT_SECRET);

    if (!payload) {
      return c.json({ error: 'Token non valido o scaduto' }, 401);
    }

    // Ruolo e classe si leggono dal database a ogni richiesta, non dal token: un token dura 30
    // giorni e prima continuava a valere anche dopo l'eliminazione dell'account, un cambio di
    // ruolo o un cambio di password.
    const user = await c.env.DB.prepare('SELECT role, class_id, password_hash FROM users WHERE id = ?')
      .bind(payload.sub)
      .first<{ role: UserRole; class_id: string | null; password_hash: string }>();
    if (!user) {
      return c.json({ error: 'Account non trovato: accedi di nuovo' }, 401);
    }
    // I token senza `pv` sono di prima di questo controllo: valgono finché non scadono.
    if (payload.pv && payload.pv !== (await passwordVersion(user.password_hash, c.env.JWT_SECRET))) {
      return c.json({ error: 'La password è cambiata: accedi di nuovo' }, 401);
    }

    c.set('jwtPayload', { ...payload, role: user.role, classId: user.class_id || 'DEFAULT_CLASS' });
    await next();
  };
}

/** Token per un utente appena autenticato (login, registrazione, cambio/reset password). */
export async function issueToken(
  env: Env,
  user: { id: string; username: string; role: UserRole; classId: string; passwordHash: string }
): Promise<string> {
  return signJWT(
    {
      sub: user.id,
      username: user.username,
      role: user.role,
      classId: user.classId,
      pv: await passwordVersion(user.passwordHash, env.JWT_SECRET),
    },
    env.JWT_SECRET
  );
}

export function requireRole(...roles: UserRole[]) {
  return async (c: Context<{ Bindings: Env; Variables: AuthVariables }>, next: Next) => {
    const payload = c.get('jwtPayload') as JWTPayload | undefined;
    if (!payload) {
      return c.json({ error: 'Autenticazione richiesta' }, 401);
    }
    if (!roles.includes(payload.role)) {
      return c.json({ error: 'Permessi insufficienti' }, 403);
    }
    await next();
  };
}

// ---------------------------------------------------------------------------
// Classe dell'utente della richiesta
// ---------------------------------------------------------------------------

/**
 * Restituisce la classe di chi sta facendo la richiesta.
 *
 * Sta qui, in un solo punto, perché ogni rotta di contenuti deve filtrare su di essa: prima il
 * backend assumeva "una classe sola implicita" (app_config.class_id = 'DEFAULT_CLASS') e quindi
 * bastava che si registrasse uno studente di un'altra classe per vedere bacheca, calendario,
 * mappa posti e sondaggi altrui.
 *
 * Normalmente la classe arriva dal token, che la porta dal login. I token emessi prima di questo
 * cambiamento però non ce l'hanno: invece di invalidarli tutti (= rifare il login a tutta la
 * classe dopo un aggiornamento) in quel caso la si rilegge dal database.
 */
export async function resolveClassId(
  c: Context<{ Bindings: Env; Variables: AuthVariables }>
): Promise<string> {
  const payload = c.get('jwtPayload') as JWTPayload | undefined;
  if (!payload) return 'DEFAULT_CLASS';
  if (payload.classId) return payload.classId;

  const row = await c.env.DB.prepare('SELECT class_id FROM users WHERE id = ?')
    .bind(payload.sub)
    .first<{ class_id: string | null }>();
  return row?.class_id || 'DEFAULT_CLASS';
}

/**
 * Garantisce che esista una riga `classes` per [classId], creandola se manca.
 *
 * La registrazione (`POST /api/auth/register`) fa già `INSERT OR IGNORE INTO classes` per la
 * classe scelta, quindi in teoria questa riga esiste sempre — ma un account può restare "orfano"
 * (class_id valorizzato sugli utenti, nessuna riga `classes` corrispondente) se si è registrato
 * prima che quell'INSERT esistesse, o per qualunque altra causa non prevista. Quando succede,
 * ogni `UPDATE classes SET ... WHERE id = ?` colpisce silenziosamente ZERO righe (nessun errore:
 * un UPDATE senza righe corrispondenti "riesce" comunque) e ogni lettura torna vuota — è esattamente
 * il bug per cui aprire la votazione preferenze restava sempre "chiusa" per una classe del genere.
 * Chiamata da preferences.ts prima di leggere/scrivere `classes`, così l'anomalia si autoripara al
 * primo utilizzo invece di richiedere un intervento manuale sul database ogni volta che si ripresenta.
 *
 * L'etichetta si ricava invertendo [classIdFromLabel] (es. "CLASS_4_CSA" -> "4 CSA"): non c'è
 * altro posto dove sia salvata l'etichetta leggibile di un classId "orfano". Non c'è un caso
 * speciale per 'DEFAULT_CLASS': storicamente esisteva sempre grazie al seed della migration, ma
 * quel seed collideva con `label UNIQUE` non appena una classe "4 CSA" si registrava anche con il
 * nuovo classId — le due righe non potevano coesistere, ed è esattamente la causa di questo bug
 * (vedi la migrazione dati che ha accorpato 'DEFAULT_CLASS' dentro 'CLASS_4_CSA'). Con IGNORE,
 * se `label` collide di nuovo con una riga già esistente per un altro classId, l'INSERT viene
 * scartato invece di lanciare — non silenzioso e basta: chi chiama farebbe comunque una query a
 * vuoto subito dopo, quindi il sintomo (mai davvero "aperta") ricomparirebbe visibilmente in fretta
 * invece di restare nascosto per mesi.
 */
export async function ensureClassRow(env: Env, classId: string): Promise<void> {
  const label = classId.startsWith('CLASS_')
    ? classId.slice('CLASS_'.length).replace(/_/g, ' ')
    : classId;
  await env.DB.prepare('INSERT OR IGNORE INTO classes (id, label) VALUES (?, ?)').bind(classId, label).run();
}

/**
 * Etichetta di una classe pulita e confrontabile: "  4^csa " e "4 CSA" devono essere la stessa
 * classe, altrimenti l'elenco si riempie di doppioni che nessuno può unire.
 * Restituisce null se l'etichetta non ha la forma "<anno 1-5> <sezione>".
 */
export function normalizeClassLabel(raw: string): string | null {
  const cleaned = raw.trim().toUpperCase().replace(/\s+/g, ' ');

  // Accetta "4 CSA", "4CSA", "4^CSA", "4^ CSA", "4° CSA", "4ª CSA" e li riporta tutti a "4 CSA".
  const match = cleaned.match(/^([1-5])\s*[\^°ª]?\s*([A-Z]{1,5})$/);
  if (!match) return null;
  return `${match[1]} ${match[2]}`;
}

/** Id stabile e leggibile ricavato dall'etichetta: "4 CSA" -> "CLASS_4_CSA". */
export function classIdFromLabel(label: string): string {
  return `CLASS_${label.replace(/\s+/g, '_')}`;
}

// ---------------------------------------------------------------------------
// Utility: generate UUID v4
// ---------------------------------------------------------------------------
export function newUUID(): string {
  return crypto.randomUUID();
}
