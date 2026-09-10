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
    const key = await getHmacKey(secret);
    const valid = await crypto.subtle.verify(
      'HMAC',
      key,
      base64url.decode(signature),
      new TextEncoder().encode(`${header}.${body}`)
    );

    if (!valid) return null;

    const payload = JSON.parse(new TextDecoder().decode(base64url.decode(body))) as JWTPayload;
    if (payload.exp < Math.floor(Date.now() / 1000)) return null;

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
  return hash === storedHash;
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

    c.set('jwtPayload', payload);
    await next();
  };
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
