// Test del Web Push. La libreria (@block65/webcrypto-web-push) si controlla con un decifratore e
// un verificatore scritti qui, a loro volta validati sui vettori di prova degli RFC:
// RFC 8291 sez. 5 / appendice A (aes128gcm) e RFC 8292 sez. 2.4 (JWT VAPID).
import { describe, expect, it } from 'vitest';
import { encryptNotification, vapidHeaders } from '@block65/webcrypto-web-push';
import { isMuted } from '../src/services/webpush';

const b64 = (s: string) => new Uint8Array(Buffer.from(s.replace(/\s+/g, ''), 'base64url'));
const txt = (b: Uint8Array) => new TextDecoder().decode(b);
const concat = (...parts: Uint8Array[]) => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
};

// --- Vettori RFC 8291 -------------------------------------------------------
const RFC_BODY = b64(`DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml
  mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT
  pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN`);
const UA_PUBLIC = `BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4`;
const UA_PRIVATE = 'q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94';
const AUTH_SECRET = 'BTBZMqHH6r4Tts7J_aSIgg';
const RFC_PLAINTEXT = 'When I grow up, I want to be a watermelon';

async function hkdf(salt: Uint8Array, ikm: Uint8Array, info: Uint8Array, bytes: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits']);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt, info }, key, bytes * 8));
}

// Decifratore lato browser (user agent) secondo RFC 8291 + RFC 8188, record unico.
async function decrypt(body: Uint8Array, uaPublicB64: string, uaPrivateB64: string, authB64: string) {
  const salt = body.slice(0, 16);
  const idlen = body[20];
  const asPublic = body.slice(21, 21 + idlen);
  const record = body.slice(21 + idlen);
  const uaPublic = b64(uaPublicB64);

  const uaKey = await crypto.subtle.importKey('jwk', {
    kty: 'EC', crv: 'P-256', d: uaPrivateB64,
    x: Buffer.from(uaPublic.slice(1, 33)).toString('base64url'),
    y: Buffer.from(uaPublic.slice(33, 65)).toString('base64url'),
  }, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']);
  const asKey = await crypto.subtle.importKey('raw', asPublic, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const ecdh = new Uint8Array(await crypto.subtle.deriveBits({ name: 'ECDH', public: asKey }, uaKey, 256));

  const enc = new TextEncoder();
  const keyInfo = concat(enc.encode('WebPush: info\0'), uaPublic, asPublic);
  const ikm = await hkdf(b64(authB64), ecdh, keyInfo, 32);
  const cek = await hkdf(salt, ikm, enc.encode('Content-Encoding: aes128gcm\0'), 16);
  const nonce = await hkdf(salt, ikm, enc.encode('Content-Encoding: nonce\0'), 12);

  const aes = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['decrypt']);
  const padded = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce }, aes, record));
  // Toglie il padding: zeri in coda e il delimitatore 0x02 dell'ultimo record.
  let end = padded.length - 1;
  while (end >= 0 && padded[end] === 0) end--;
  expect(padded[end]).toBe(2);
  return { plaintext: padded.slice(0, end), ikm, cek, nonce, rs: new DataView(body.buffer, body.byteOffset + 16, 4).getUint32(0) };
}

describe('RFC 8291 (aes128gcm)', () => {
  it('il decifratore di prova riproduce i valori intermedi e il testo del vettore RFC', async () => {
    const r = await decrypt(RFC_BODY, UA_PUBLIC, UA_PRIVATE, AUTH_SECRET);
    expect(Buffer.from(r.ikm).toString('base64url')).toBe('S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg');
    expect(Buffer.from(r.cek).toString('base64url')).toBe('oIhVW04MRdy2XN9CiKLxTg');
    expect(Buffer.from(r.nonce).toString('base64url')).toBe('4h_95klXJ5E_qnoN');
    expect(r.rs).toBe(4096);
    expect(txt(r.plaintext)).toBe(RFC_PLAINTEXT);
  });

  it('la libreria cifra per le chiavi del vettore RFC e il messaggio torna identico', async () => {
    const message = JSON.stringify({ title: 'Nuova circolare', body: 'N. 42 — Uscita didattica', kind: 'circulars' });
    const body = await encryptNotification(
      { endpoint: 'https://push.example.net/x', expirationTime: null, keys: { p256dh: UA_PUBLIC, auth: AUTH_SECRET } },
      new TextEncoder().encode(message)
    );
    const r = await decrypt(new Uint8Array(body), UA_PUBLIC, UA_PRIVATE, AUTH_SECRET);
    expect(txt(r.plaintext)).toBe(message);
  });
});

// --- RFC 8292 (VAPID) -------------------------------------------------------
async function verifyJwt(jwt: string, publicKeyB64: string): Promise<Record<string, unknown>> {
  const [h, p, s] = jwt.split('.');
  const key = await crypto.subtle.importKey('raw', b64(publicKeyB64), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, b64(s), new TextEncoder().encode(`${h}.${p}`));
  expect(ok).toBe(true);
  expect(JSON.parse(txt(b64(h)))).toMatchObject({ alg: 'ES256' });
  return JSON.parse(txt(b64(p)));
}

describe('RFC 8292 (VAPID)', () => {
  it('il verificatore di prova accetta il JWT di esempio della RFC', async () => {
    const jwt = `eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJodHRwczovL3
      B1c2guZXhhbXBsZS5uZXQiLCJleHAiOjE0NTM1MjM3NjgsInN1YiI6Im1ha
      Wx0bzpwdXNoQGV4YW1wbGUuY29tIn0.i3CYb7t4xfxCDquptFOepC9GAu_H
      LGkMlMuCGSK2rpiUfnK9ojFwDXb1JrErtmysazNjjvW2L9OkSSHzvoD1oA`.replace(/\s+/g, '');
    const k = 'BA1Hxzyi1RUM1b5wjxsn7nGxAszw2u61m164i3MrAIxHF6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs';
    const claims = await verifyJwt(jwt, k);
    expect(claims).toEqual({ aud: 'https://push.example.net', exp: 1453523768, sub: 'mailto:push@example.com' });
  });

  it('la libreria produce un header "vapid t=…, k=…" valido', async () => {
    const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign']) as CryptoKeyPair;
    const publicKey = Buffer.from(await crypto.subtle.exportKey('raw', pair.publicKey) as ArrayBuffer).toString('base64url');
    const privateKey = (await crypto.subtle.exportKey('jwk', pair.privateKey)).d!;

    const { headers } = await vapidHeaders(
      { endpoint: 'https://web.push.apple.com/abc', expirationTime: null, keys: { p256dh: UA_PUBLIC, auth: AUTH_SECRET } },
      { subject: 'mailto:test@example.com', publicKey, privateKey }
    );
    const match = /^vapid t=([^,]+), k=(.+)$/.exec(headers.authorization);
    expect(match).not.toBeNull();
    expect(match![2]).toBe(publicKey);

    const claims = await verifyJwt(match![1], publicKey);
    expect(claims.aud).toBe('https://web.push.apple.com');
    expect(claims.sub).toBe('mailto:test@example.com');
    const now = Math.floor(Date.now() / 1000);
    expect(claims.exp as number).toBeGreaterThan(now);
    expect(claims.exp as number).toBeLessThanOrEqual(now + 24 * 60 * 60);
  });
});

describe('preferenze per categoria', () => {
  it('salta solo le categorie silenziate', () => {
    expect(isMuted('circulars,board', 'board')).toBe(true);
    expect(isMuted('circulars,board', 'polls')).toBe(false);
    expect(isMuted('', 'circulars')).toBe(false);
    expect(isMuted('circulars', '')).toBe(false);
  });
});
