// Push a topic FCM: chi e' iscritto riceve solo il messaggio del topic, gli altri quello per token.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import { notifyClass, planDelivery, topicName } from '../src/services/fcm';
import type { Env } from '../src/types';

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
let env: Env;
let sent: Record<string, any>[];

function pem(der: ArrayBuffer): string {
  const b64 = btoa(String.fromCharCode(...new Uint8Array(der)));
  return `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----`;
}

beforeEach(async () => {
  const { d1 } = createD1();
  const pair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify']
  );
  const key = pem(await crypto.subtle.exportKey('pkcs8', pair.privateKey));
  env = {
    DB: d1,
    JWT_SECRET: 'test-secret',
    FCM_PROJECT_ID: 'progetto',
    FCM_SERVICE_ACCOUNT_KEY: JSON.stringify({ client_email: 'sa@test', private_key: key }),
  } as unknown as Env;

  sent = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('oauth2')) {
        return new Response(JSON.stringify({ access_token: 'at', expires_in: 3600 }), { status: 200 });
      }
      sent.push(JSON.parse(String(init?.body)).message);
      return new Response('{}', { status: 200 });
    })
  );
});

afterEach(() => vi.unstubAllGlobals());

async function call(method: string, path: string, body?: unknown, token?: string) {
  const res = await worker.fetch(
    new Request(`https://api.test${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
    env,
    ctx
  );
  return { status: res.status, json: (await res.json().catch(() => ({}))) as Record<string, any> };
}

async function signup(username: string, classLabel = '3 B') {
  const r = await call('POST', '/api/auth/register', {
    firstName: 'Nome', lastName: username, username, password: 'password123', heightCm: 170, classLabel,
  });
  if (r.status !== 201) throw new Error(JSON.stringify(r));
  return { token: r.json.token as string, id: r.json.user.id as string };
}

describe('nomi dei topic', () => {
  it('sono stabili, segreti e con soli caratteri ammessi', async () => {
    const school = await topicName(env, 'school');
    const cls = await topicName(env, { classId: 'abc' });
    expect(school).toMatch(/^aila_s_[A-Za-z0-9_-]{16}$/);
    expect(cls).toMatch(/^aila_c_[A-Za-z0-9_-]{16}$/);
    expect(await topicName(env, 'school')).toBe(school);
    expect(await topicName(env, { classId: 'xyz' })).not.toBe(cls);
    const other = { ...env, JWT_SECRET: 'altro' } as Env;
    expect(await topicName(other, 'school')).not.toBe(school);
  });

  it('GET /api/fcm/topics li da a chi e\' autenticato', async () => {
    expect((await call('GET', '/api/fcm/topics')).status).toBe(401);
    const u = await signup('mario');
    const r = await call('GET', '/api/fcm/topics', undefined, u.token);
    expect(r.status).toBe(200);
    expect(r.json.school).toBe(await topicName(env, 'school'));
    expect(r.json.class).toMatch(/^aila_c_/);
  });
});

describe('scelta dei destinatari', () => {
  it('un Android iscritto riceve solo il topic', () => {
    const plan = planDelivery([
      { token: 'a1', platform: 'android', topic: 1 },
      { token: 'a2', platform: 'android', topic: 0 },
      { token: 'i1', platform: 'ios', topic: 1 },
    ]);
    expect(plan.topic).toBe(true);
    expect(plan.perToken.map((r) => r.token)).toEqual(['a2', 'i1']);
  });

  it('senza iscritti non si manda nulla al topic', () => {
    expect(planDelivery([{ token: 'a2', platform: 'android' }]).topic).toBe(false);
  });
});

describe('notifyClass', () => {
  it('iscritto: una richiesta al topic, niente messaggio singolo; APK vecchio e iOS per token', async () => {
    const a = await signup('utente_a');
    const b = await signup('utente_b');
    const c = await signup('utente_c');
    await call('POST', '/api/fcm/token', { token: 'tok-nuovo', platform: 'android' }, a.token);
    await call('POST', '/api/fcm/token', { token: 'tok-vecchio', platform: 'android' }, b.token);
    await call('POST', '/api/fcm/token', { token: 'tok-ios', platform: 'ios' }, c.token);
    expect((await call('POST', '/api/fcm/topics/subscribed', { token: 'tok-nuovo' }, a.token)).status).toBe(200);

    const classId = (await env.DB.prepare('SELECT class_id FROM users WHERE id = ?').bind(a.id).first<{ class_id: string }>())!.class_id;
    await notifyClass(env, 'Titolo', 'Testo', { action: 'new_proposal' }, classId);

    const topics = sent.filter((m) => m.topic);
    const tokens = sent.filter((m) => m.token).map((m) => m.token).sort();
    expect(topics).toHaveLength(1);
    expect(topics[0].topic).toBe(await topicName(env, { classId }));
    expect(topics[0].data.title).toBe('Titolo');
    expect(topics[0].notification).toBeUndefined();
    expect(tokens).toEqual(['tok-ios', 'tok-vecchio']);
  });

  it('senza classe va al topic dell\'istituto', async () => {
    const a = await signup('utente_a');
    await call('POST', '/api/fcm/token', { token: 'tok-nuovo', platform: 'android' }, a.token);
    await call('POST', '/api/fcm/topics/subscribed', { token: 'tok-nuovo' }, a.token);
    await notifyClass(env, 'Circolare', 'Testo', { action: 'new_circular' });
    expect(sent).toHaveLength(1);
    expect(sent[0].topic).toBe(await topicName(env, 'school'));
  });

  it('non si puo\' segnare iscritto il token di un altro', async () => {
    const a = await signup('utente_a');
    const b = await signup('utente_b');
    await call('POST', '/api/fcm/token', { token: 'tok-a', platform: 'android' }, a.token);
    await call('POST', '/api/fcm/topics/subscribed', { token: 'tok-a' }, b.token);
    const row = await env.DB.prepare('SELECT COUNT(*) AS n FROM fcm_topic_devices').first<{ n: number }>();
    expect(row!.n).toBe(0);
  });

  it('logout e cambio account tolgono il telefono dal registro', async () => {
    const a = await signup('utente_a');
    const b = await signup('utente_b');
    await call('POST', '/api/fcm/token', { token: 'tok', platform: 'android' }, a.token);
    await call('POST', '/api/fcm/topics/subscribed', { token: 'tok' }, a.token);
    // Un altro account entra sullo stesso telefono senza logout: non eredita l'iscrizione.
    await call('POST', '/api/fcm/token', { token: 'tok', platform: 'android' }, b.token);
    const count = () => env.DB.prepare('SELECT COUNT(*) AS n FROM fcm_topic_devices').first<{ n: number }>().then((r) => r!.n);
    expect(await count()).toBe(0);

    await call('POST', '/api/fcm/topics/subscribed', { token: 'tok' }, b.token);
    expect(await count()).toBe(1);
    await call('DELETE', '/api/fcm/token', {}, b.token);
    expect(await count()).toBe(0);
  });
});
