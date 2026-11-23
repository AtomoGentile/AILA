// =============================================================================
// AILA — WebAuthn / Passkey verification (Web Crypto API, zero dipendenze esterne)
// Supporta ES256 (ECDSA P-256, alg -7) e RS256 (RSASSA-PKCS1-v1_5 SHA-256, alg -257).
// =============================================================================

import type { Env } from '../types';
import { timingSafeEqual } from '../auth';

const LOCALHOST_ORIGIN = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
const SHARED_PWA_RP_ID = 'aila-scuola.pages.dev';
const DEFAULT_ALLOWED_ORIGINS = [
  'https://aila-scuola.pages.dev',
  'https://beta.aila-scuola.pages.dev',
];

export const PASSKEY_SUPPORTED_ALGORITHMS = new Set([-7, -257]);

export function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export function base64UrlDecode(input: string): Uint8Array {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (input.length % 4)) % 4);
  const binary = atob(padded);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

export function randomChallenge(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

/**
 * Ricava origin e rpId ammessi dall'header Origin.
 * Sia `aila-scuola.pages.dev` sia `beta.aila-scuola.pages.dev` condividono `rpId = "aila-scuola.pages.dev"`,
 * cosi' una passkey registrata sulla stabile funziona anche sulla beta e viceversa.
 */
export function resolvePasskeyOriginAndRpId(
  originHeader: string | undefined,
  env: Env
): { origin: string; rpId: string } | null {
  // Le app native Android/iOS (client HTTP Ktor) non inviano l'header browser `Origin`:
  // usano il dominio canonico AILA per legare le chiavi biometriche del dispositivo.
  // I browser invece inviano sempre `Origin` su POST cross-origin, quindi una pagina malevola
  // viene sempre filtrata dalla whitelist qui sotto.
  if (!originHeader) {
    return { origin: `https://${SHARED_PWA_RP_ID}`, rpId: SHARED_PWA_RP_ID };
  }
  let parsed: URL;
  try {
    parsed = new URL(originHeader);
  } catch {
    return null;
  }
  const normalized = parsed.origin;
  const configured = (env.WEB_ORIGINS ?? '')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean);
  const allowed =
    DEFAULT_ALLOWED_ORIGINS.includes(normalized) ||
    configured.includes(normalized) ||
    LOCALHOST_ORIGIN.test(normalized);
  if (!allowed) return null;

  const host = parsed.hostname.toLowerCase();
  const rpId = host === SHARED_PWA_RP_ID || host.endsWith(`.${SHARED_PWA_RP_ID}`)
    ? SHARED_PWA_RP_ID
    : host;
  return { origin: normalized, rpId };
}

export function verifyClientData(
  clientDataBytes: Uint8Array,
  expectedType: 'webauthn.create' | 'webauthn.get',
  expectedChallenge: string,
  expectedOrigin: string
): boolean {
  let json: { type?: unknown; challenge?: unknown; origin?: unknown };
  try {
    json = JSON.parse(new TextDecoder().decode(clientDataBytes));
  } catch {
    return false;
  }
  if (json.type !== expectedType) return false;
  if (typeof json.challenge !== 'string' || !timingSafeEqual(json.challenge, expectedChallenge)) return false;
  if (typeof json.origin !== 'string' || json.origin !== expectedOrigin) return false;
  return true;
}

export async function verifyAuthenticatorData(
  authData: Uint8Array,
  expectedRpId: string
): Promise<{ ok: true; signCount: number } | { ok: false }> {
  // Struttura WebAuthn authenticatorData: rpIdHash (32) + flags (1) + signCount (4) = minimo 37 byte.
  if (authData.length < 37) return { ok: false };
  const expectedHash = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(expectedRpId)));
  const receivedHash = authData.subarray(0, 32);
  let diff = 0;
  for (let i = 0; i < 32; i++) diff |= expectedHash[i] ^ receivedHash[i];
  if (diff !== 0) return { ok: false };

  const flags = authData[32];
  // Bit 0 (0x01): User Present (UP) obbligatorio per ogni operazione WebAuthn.
  if ((flags & 0x01) === 0) return { ok: false };

  const view = new DataView(authData.buffer, authData.byteOffset + 33, 4);
  const signCount = view.getUint32(0, false);
  return { ok: true, signCount };
}

/**
 * Converte una firma ECDSA ASN.1 DER (`SEQUENCE { INTEGER r, INTEGER s }`) nei 64 byte raw `r || s`
 * richiesti da Web Crypto API (`crypto.subtle.verify` con ECDSA P-256).
 */
export function derToRawEcdsaP256(der: Uint8Array): Uint8Array | null {
  if (der.length < 8 || der[0] !== 0x30) return null;
  let offset = 1;
  let seqLen = der[offset++];
  if (seqLen & 0x80) {
    const lenBytes = seqLen & 0x7f;
    if (lenBytes === 0 || lenBytes > 2 || offset + lenBytes > der.length) return null;
    seqLen = 0;
    for (let i = 0; i < lenBytes; i++) seqLen = (seqLen << 8) | der[offset++];
  }
  if (offset + seqLen !== der.length) return null;

  function readInt(): Uint8Array | null {
    if (offset >= der.length || der[offset++] !== 0x02) return null;
    if (offset >= der.length) return null;
    const len = der[offset++];
    if (len === 0 || offset + len > der.length) return null;
    let slice = der.subarray(offset, offset + len);
    offset += len;
    // Rimuove eventuale 0x00 di segno positivo all'inizio.
    while (slice.length > 32 && slice[0] === 0x00) slice = slice.subarray(1);
    if (slice.length > 32) return null;
    const padded = new Uint8Array(32);
    padded.set(slice, 32 - slice.length);
    return padded;
  }

  const r = readInt();
  const s = readInt();
  if (!r || !s || offset !== der.length) return null;
  const raw = new Uint8Array(64);
  raw.set(r, 0);
  raw.set(s, 32);
  return raw;
}

export async function verifyPasskeySignature(
  publicKeySpkiB64: string,
  algorithm: number,
  authData: Uint8Array,
  clientDataBytes: Uint8Array,
  signatureBytes: Uint8Array
): Promise<boolean> {
  if (!PASSKEY_SUPPORTED_ALGORITHMS.has(algorithm)) return false;
  const spki = base64UrlDecode(publicKeySpkiB64);
  const clientDataHash = new Uint8Array(await crypto.subtle.digest('SHA-256', clientDataBytes));
  const signedData = new Uint8Array(authData.length + clientDataHash.length);
  signedData.set(authData, 0);
  signedData.set(clientDataHash, authData.length);

  try {
    if (algorithm === -7) {
      const key = await crypto.subtle.importKey(
        'spki',
        spki,
        { name: 'ECDSA', namedCurve: 'P-256' },
        false,
        ['verify']
      );
      const rawSig = signatureBytes.length === 64 ? signatureBytes : derToRawEcdsaP256(signatureBytes);
      if (!rawSig) return false;
      return await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, rawSig, signedData);
    }
    const key = await crypto.subtle.importKey(
      'spki',
      spki,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['verify']
    );
    return await crypto.subtle.verify({ name: 'RSASSA-PKCS1-v1_5' }, key, signatureBytes, signedData);
  } catch {
    return false;
  }
}

export async function validateSpkiPublicKey(publicKeySpkiB64: string, algorithm: number): Promise<boolean> {
  if (!PASSKEY_SUPPORTED_ALGORITHMS.has(algorithm)) return false;
  try {
    const spki = base64UrlDecode(publicKeySpkiB64);
    if (spki.length < 32 || spki.length > 2048) return false;
    if (algorithm === -7) {
      await crypto.subtle.importKey('spki', spki, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
    } else {
      await crypto.subtle.importKey('spki', spki, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    }
    return true;
  } catch {
    return false;
  }
}
