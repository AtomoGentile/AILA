import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import type { Env } from '../src/types';

vi.mock('../src/services/fcm', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../src/services/fcm')>()),
  notifyUser: async () => {},
  notifyUsers: async () => {},
}));

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
const ADMIN = 'admin-secret-di-prova-1234';
let env: Env;

beforeEach(() => {
  const { d1 } = createD1();
  env = { DB: d1, JWT_SECRET: 'test-secret', ADMIN_SECRET: ADMIN } as unknown as Env;
});

async function call(method: string, path: string, body?: unknown, token?: string, headers: Record<string, string> = {}) {
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

describe('recupero credenziali e username in Scheda Classe', () => {
  it('espone username in GET /api/ratings solo al Rappresentante e fa trim dello username al login', async () => {
    const repInvite = await call(
      'POST',
      '/api/admin/representative-invites',
      { classLabel: '3 B' },
      undefined,
      { 'X-Admin-Secret': ADMIN }
    );
    expect(repInvite.status).toBe(201);
    const repCode = repInvite.json.codes[0].code;

    const rep = await call('POST', '/api/auth/register', {
      firstName: 'Simone',
      lastName: 'Bianchin',
      username: 'simone.rep',
      password: 'password123',
      heightCm: 175,
      classLabel: '3 B',
      representativeCode: repCode,
    });
    expect(rep.status).toBe(201);
    const repToken = rep.json.token as string;
    const classCode = (await call('GET', '/api/users/class-code', undefined, repToken)).json.code as string;

    const student = await call('POST', '/api/auth/register', {
      firstName: 'Mario',
      lastName: 'Rossi',
      username: 'mario.rossi',
      password: 'password123',
      heightCm: 170,
      classLabel: '3 B',
      classCode,
    });
    expect(student.status).toBe(201);
    const studentToken = student.json.token as string;

    // Lo studente non puo' accedere a /api/ratings.
    const forbidden = await call('GET', '/api/ratings', undefined, studentToken);
    expect(forbidden.status).toBe(403);

    // Il Rappresentante vede anche lo username di ciascun compagno nella Scheda Classe.
    const roster = await call('GET', '/api/ratings', undefined, repToken);
    expect(roster.status).toBe(200);
    const marioRow = roster.json.ratings.find((r: any) => r.studentId === student.json.user.id);
    expect(marioRow?.username).toBe('mario.rossi');

    // Login con spazi accidentali prima/dopo lo username (es. tastiera iOS/incolla) riesce.
    const login = await call('POST', '/api/auth/login', {
      username: '  Mario.Rossi  ',
      password: 'password123',
    });
    expect(login.status).toBe(200);
    expect(login.json.user.username).toBe('mario.rossi');
  });
});
