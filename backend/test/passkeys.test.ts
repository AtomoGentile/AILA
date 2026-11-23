import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import type { Env } from '../src/types';
import { base64UrlEncode } from '../src/services/passkeys';

vi.mock('../src/services/fcm', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../src/services/fcm')>()),
  notifyUser: async () => {},
  notifyUsers: async () => {},
}));

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
const ADMIN = 'admin-secret-di-prova-1234';
const STABLE_ORIGIN = 'https://aila-scuola.pages.dev';
const BETA_ORIGIN = 'https://beta.aila-scuola.pages.dev';
let env: Env;

beforeEach(() => {
  const { d1 } = createD1();
  env = { DB: d1, JWT_SECRET: 'test-secret', ADMIN_SECRET: ADMIN } as unknown as Env;
});

async function call(
  method: string,
  path: string,
  body?: unknown,
  token?: string,
  headers: Record<string, string> = {}
) {
  const res = await worker.fetch(
    new Request(`https://api.test${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
    env,
    ctx
  );
  const json = (await res.json().catch(() => ({}))) as Record<string, any>;
  return { status: res.status, json };
}

function encodeDerInteger(bytes: Uint8Array): Uint8Array {
  let start = 0;
  while (start < bytes.length - 1 && bytes[start] === 0) start++;
  const trimmed = bytes.slice(start);
  const needsLeadingZero = (trimmed[0] & 0x80) !== 0;
  const out = new Uint8Array(2 + (needsLeadingZero ? 1 : 0) + trimmed.length);
  out[0] = 0x02;
  out[1] = trimmed.length + (needsLeadingZero ? 1 : 0);
  if (needsLeadingZero) {
    out[2] = 0x00;
    out.set(trimmed, 3);
  } else {
    out.set(trimmed, 2);
  }
  return out;
}

function rawP256ToDer(raw: Uint8Array): Uint8Array {
  const r = encodeDerInteger(raw.slice(0, 32));
  const s = encodeDerInteger(raw.slice(32, 64));
  const seq = new Uint8Array(2 + r.length + s.length);
  seq[0] = 0x30;
  seq[1] = r.length + s.length;
  seq.set(r, 2);
  seq.set(s, 2 + r.length);
  return seq;
}

async function buildAuthenticatorData(rpId: string, signCount: number, flags = 0x05): Promise<Uint8Array> {
  const rpHash = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rpId)));
  const out = new Uint8Array(37);
  out.set(rpHash, 0);
  out[32] = flags; // UP (0x01) + UV (0x04)
  const view = new DataView(out.buffer);
  view.setUint32(33, signCount, false);
  return out;
}

async function signAssertion(
  privateKey: CryptoKey,
  authData: Uint8Array,
  clientDataBytes: Uint8Array
): Promise<Uint8Array> {
  const clientHash = new Uint8Array(await crypto.subtle.digest('SHA-256', clientDataBytes));
  const signed = new Uint8Array(authData.length + clientHash.length);
  signed.set(authData, 0);
  signed.set(clientHash, authData.length);
  const rawSig = new Uint8Array(
    await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, privateKey, signed)
  );
  return rawP256ToDer(rawSig);
}

async function registerTestUser(): Promise<{ token: string; userId: string; username: string }> {
  const repInvite = await call(
    'POST',
    '/api/admin/representative-invites',
    { classLabel: '3 B' },
    undefined,
    { 'X-Admin-Secret': ADMIN }
  );
  const repCode = repInvite.json.codes[0].code;
  const rep = await call('POST', '/api/auth/register', {
    firstName: 'Simone',
    lastName: 'Bianchin',
    username: 'simone.passkey',
    password: 'password123',
    heightCm: 175,
    classLabel: '3 B',
    representativeCode: repCode,
  });
  expect(rep.status).toBe(201);
  return {
    token: rep.json.token as string,
    userId: rep.json.user.id as string,
    username: rep.json.user.username as string,
  };
}

describe('WebAuthn Passkeys / Face ID (/api/auth/passkeys)', () => {
  it('registra una passkey, fa login usernameless anche su beta, blocca replay/manomissioni e revoca', async () => {
    const user = await registerTestUser();

    // Origine non autorizzata -> 403
    const badOrigin = await call(
      'POST',
      '/api/auth/passkeys/register/options',
      {},
      user.token,
      { Origin: 'https://evil.example' }
    );
    expect(badOrigin.status).toBe(403);

    // 1. Richiedi opzioni di registrazione da stable
    const regOpts = await call(
      'POST',
      '/api/auth/passkeys/register/options',
      {},
      user.token,
      { Origin: STABLE_ORIGIN }
    );
    expect(regOpts.status).toBe(200);
    expect(regOpts.json.rpId).toBe('aila-scuola.pages.dev');
    expect(regOpts.json.username).toBe('simone.passkey');

    // Genera coppia ECDSA P-256 reale come farebbe Face ID / iCloud Keychain
    const keyPair = (await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    )) as CryptoKeyPair;
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', keyPair.publicKey));
    const credentialId = base64UrlEncode(crypto.getRandomValues(new Uint8Array(24)));

    const regClientData = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.create',
        challenge: regOpts.json.challenge,
        origin: STABLE_ORIGIN,
      })
    );
    const regAuthData = await buildAuthenticatorData('aila-scuola.pages.dev', 1);

    // 2. Completa registrazione
    const regVerify = await call(
      'POST',
      '/api/auth/passkeys/register/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(regClientData),
        authenticatorData: base64UrlEncode(regAuthData),
        publicKeySpki: base64UrlEncode(spki),
        algorithm: -7,
        label: 'iPhone di Simone',
      },
      user.token,
      { Origin: STABLE_ORIGIN }
    );
    expect(regVerify.status).toBe(201);
    expect(regVerify.json.credentialId).toBe(credentialId);
    expect(regVerify.json.label).toBe('iPhone di Simone');

    // 3. Elenco passkey
    const list = await call('GET', '/api/auth/passkeys', undefined, user.token);
    expect(list.status).toBe(200);
    expect(list.json.passkeys).toHaveLength(1);
    expect(list.json.passkeys[0].label).toBe('iPhone di Simone');

    // 4. Login usernameless da BETA origin usando la stessa passkey registrata su STABLE!
    const loginOpts = await call(
      'POST',
      '/api/auth/passkeys/login/options',
      {},
      undefined,
      { Origin: BETA_ORIGIN }
    );
    expect(loginOpts.status).toBe(200);
    expect(loginOpts.json.rpId).toBe('aila-scuola.pages.dev');
    expect(loginOpts.json.allowCredentialIds).toEqual([]);

    const loginClientData = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.get',
        challenge: loginOpts.json.challenge,
        origin: BETA_ORIGIN,
      })
    );
    const loginAuthData = await buildAuthenticatorData('aila-scuola.pages.dev', 2);
    const loginSig = await signAssertion(keyPair.privateKey, loginAuthData, loginClientData);

    const loginVerify = await call(
      'POST',
      '/api/auth/passkeys/login/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(loginClientData),
        authenticatorData: base64UrlEncode(loginAuthData),
        signature: base64UrlEncode(loginSig),
      },
      undefined,
      { Origin: BETA_ORIGIN }
    );
    expect(loginVerify.status).toBe(200);
    expect(loginVerify.json.success).toBe(true);
    expect(loginVerify.json.user.username).toBe('simone.passkey');
    expect(typeof loginVerify.json.token).toBe('string');

    // Il JWT emesso funziona sugli endpoint autenticati
    const me = await call('GET', '/api/users/me', undefined, loginVerify.json.token);
    expect(me.status).toBe(200);
    expect(me.json.username).toBe('simone.passkey');

    // 5. Replay della stessa sfida -> 401
    const replay = await call(
      'POST',
      '/api/auth/passkeys/login/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(loginClientData),
        authenticatorData: base64UrlEncode(loginAuthData),
        signature: base64UrlEncode(loginSig),
      },
      undefined,
      { Origin: BETA_ORIGIN }
    );
    expect(replay.status).toBe(401);

    // 6. Firma manomessa -> 401
    const loginOpts2 = await call(
      'POST',
      '/api/auth/passkeys/login/options',
      { username: 'simone.passkey' },
      undefined,
      { Origin: STABLE_ORIGIN }
    );
    expect(loginOpts2.json.allowCredentialIds).toEqual([credentialId]);
    const loginClientData2 = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.get',
        challenge: loginOpts2.json.challenge,
        origin: STABLE_ORIGIN,
      })
    );
    const loginAuthData2 = await buildAuthenticatorData('aila-scuola.pages.dev', 3);
    const tamperedSig = await signAssertion(keyPair.privateKey, loginAuthData2, loginClientData2);
    tamperedSig[tamperedSig.length - 1] ^= 0xff;

    const badSig = await call(
      'POST',
      '/api/auth/passkeys/login/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(loginClientData2),
        authenticatorData: base64UrlEncode(loginAuthData2),
        signature: base64UrlEncode(tamperedSig),
      },
      undefined,
      { Origin: STABLE_ORIGIN }
    );
    expect(badSig.status).toBe(401);

    // 7. Revoca passkey -> login successivo fallisce
    const del = await call('DELETE', `/api/auth/passkeys/${encodeURIComponent(credentialId)}`, undefined, user.token);
    expect(del.status).toBe(200);

    const loginOpts3 = await call(
      'POST',
      '/api/auth/passkeys/login/options',
      {},
      undefined,
      { Origin: STABLE_ORIGIN }
    );
    const loginClientData3 = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.get',
        challenge: loginOpts3.json.challenge,
        origin: STABLE_ORIGIN,
      })
    );
    const loginAuthData3 = await buildAuthenticatorData('aila-scuola.pages.dev', 4);
    const loginSig3 = await signAssertion(keyPair.privateKey, loginAuthData3, loginClientData3);

    const afterDelete = await call(
      'POST',
      '/api/auth/passkeys/login/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(loginClientData3),
        authenticatorData: base64UrlEncode(loginAuthData3),
        signature: base64UrlEncode(loginSig3),
      },
      undefined,
      { Origin: STABLE_ORIGIN }
    );
    expect(afterDelete.status).toBe(401);
  });

  it('supporta anche client nativi Android/iOS (senza header Origin)', async () => {
    const user = await registerTestUser();

    const regOpts = await call('POST', '/api/auth/passkeys/register/options', {}, user.token);
    expect(regOpts.status).toBe(200);
    expect(regOpts.json.rpId).toBe('aila-scuola.pages.dev');

    const keyPair = (await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    )) as CryptoKeyPair;
    const spki = new Uint8Array(await crypto.subtle.exportKey('spki', keyPair.publicKey));
    const credentialId = base64UrlEncode(crypto.getRandomValues(new Uint8Array(24)));

    const regClientData = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.create',
        challenge: regOpts.json.challenge,
        origin: STABLE_ORIGIN,
      })
    );
    const regAuthData = await buildAuthenticatorData('aila-scuola.pages.dev', 1);

    const regVerify = await call(
      'POST',
      '/api/auth/passkeys/register/verify',
      {
        credentialId,
        clientDataJSON: base64UrlEncode(regClientData),
        authenticatorData: base64UrlEncode(regAuthData),
        publicKeySpki: base64UrlEncode(spki),
        algorithm: -7,
        label: 'Android',
      },
      user.token
    );
    expect(regVerify.status).toBe(201);

    const loginOpts = await call('POST', '/api/auth/passkeys/login/options', {});
    expect(loginOpts.status).toBe(200);
    const loginClientData = new TextEncoder().encode(
      JSON.stringify({
        type: 'webauthn.get',
        challenge: loginOpts.json.challenge,
        origin: STABLE_ORIGIN,
      })
    );
    const loginAuthData = await buildAuthenticatorData('aila-scuola.pages.dev', 2);
    const loginSig = await signAssertion(keyPair.privateKey, loginAuthData, loginClientData);

    const loginVerify = await call('POST', '/api/auth/passkeys/login/verify', {
      credentialId,
      clientDataJSON: base64UrlEncode(loginClientData),
      authenticatorData: base64UrlEncode(loginAuthData),
      signature: base64UrlEncode(loginSig),
    });
    expect(loginVerify.status).toBe(200);
    expect(loginVerify.json.user.username).toBe('simone.passkey');
  });
});
