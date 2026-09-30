// Rotte vere su un D1 finto (test/d1shim.ts) con lo schema reale.
import { beforeEach, describe, expect, it } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import { anonymizePairs } from '../src/routes/preferences';
import { normalizeTitle } from '../src/routes/calendar';
import { isDeadToken } from '../src/services/fcm';
import { italianToday, upsertAnalysis } from '../src/services/summarizer';
import type { Env } from '../src/types';

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
let env: Env;

beforeEach(() => {
  const { d1 } = createD1();
  env = { DB: d1, JWT_SECRET: 'test-secret', REPRESENTATIVE_SIGNUP_CODE: 'REP-CODE' } as unknown as Env;
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

function register(username: string, extra: Record<string, unknown> = {}) {
  return call('POST', '/api/auth/register', {
    firstName: 'Nome',
    lastName: username,
    username,
    password: 'password123',
    heightCm: 170,
    classLabel: '3 B',
    ...extra,
  });
}

describe('registrazione e codice classe', () => {
  it('chiede il codice solo dopo che il Rappresentante lo ha generato', async () => {
    expect((await register('primo')).status).toBe(201);
    // Nessun codice ancora: si entra come prima.
    expect((await register('secondo')).status).toBe(201);

    const rep = await register('rappresentante', { representativeCode: 'REP-CODE' });
    expect(rep.status).toBe(201);
    const code = await call('GET', '/api/users/class-code', undefined, rep.json.token);
    expect(code.status).toBe(200);
    expect(code.json.code).toMatch(/^[A-Z2-9]{6}$/);

    const without = await register('terzo');
    expect(without.status).toBe(403);
    expect(without.json.classCodeRequired).toBe(true);
    expect((await register('terzo', { classCode: 'ZZZZZZ' })).status).toBe(403);
    expect((await register('terzo', { classCode: code.json.code.toLowerCase() })).status).toBe(201);

    // Una classe nuova non chiede codici.
    expect((await register('altra', { classLabel: '5 A' })).status).toBe(201);
  });

  it('al massimo due Rappresentanti per classe', async () => {
    expect((await register('rep1', { representativeCode: 'REP-CODE' })).status).toBe(201);
    expect((await register('rep2', { representativeCode: 'REP-CODE' })).status).toBe(201);
    expect((await register('rep3', { representativeCode: 'REP-CODE' })).status).toBe(409);
  });
});

describe('login', () => {
  it('blocca i tentativi sbagliati ripetuti', async () => {
    await register('mario');
    for (let i = 0; i < 8; i++) {
      expect((await call('POST', '/api/auth/login', { username: 'mario', password: 'sbagliata1' })).status).toBe(401);
    }
    expect((await call('POST', '/api/auth/login', { username: 'mario', password: 'password123' })).status).toBe(429);
  });

  it('un corpo non JSON e\' un 400, non un 500', async () => {
    const res = await worker.fetch(
      new Request('https://api.test/api/auth/login', { method: 'POST', body: '{nope', headers: { 'Content-Type': 'application/json' } }),
      env,
      ctx
    );
    expect(res.status).toBe(400);
  });
});

describe('password e token', () => {
  it('dopo il cambio password il token vecchio non vale piu\'', async () => {
    const { json } = await register('luca');
    const old = json.token;
    expect((await call('GET', '/api/users/me', undefined, old)).status).toBe(200);

    expect((await call('PUT', '/api/users/me/password', { currentPassword: 'sbagliata', newPassword: 'nuovapass1' }, old)).status).toBe(403);
    const changed = await call('PUT', '/api/users/me/password', { currentPassword: 'password123', newPassword: 'nuovapass1' }, old);
    expect(changed.status).toBe(200);

    expect((await call('GET', '/api/users/me', undefined, old)).status).toBe(401);
    expect((await call('GET', '/api/users/me', undefined, changed.json.token)).status).toBe(200);
    expect((await call('POST', '/api/auth/login', { username: 'luca', password: 'nuovapass1' })).status).toBe(200);
  });

  it('reset con il codice del Rappresentante, una volta sola', async () => {
    const student = await register('giulia');
    const rep = await register('capo', { representativeCode: 'REP-CODE' });
    const reset = await call('POST', `/api/users/${student.json.user.id}/reset-code`, {}, rep.json.token);
    expect(reset.status).toBe(200);
    // Uno studente non puo' generarne.
    expect((await call('POST', `/api/users/${rep.json.user.id}/reset-code`, {}, student.json.token)).status).toBe(403);

    const bad = await call('POST', '/api/auth/reset-password', { username: 'giulia', code: 'AAAAAAAA', newPassword: 'nuovapass1' });
    expect(bad.status).toBe(400);
    const ok = await call('POST', '/api/auth/reset-password', { username: 'giulia', code: reset.json.code, newPassword: 'nuovapass1' });
    expect(ok.status).toBe(200);
    expect(ok.json.user.username).toBe('giulia');
    // Il token di prima del reset non vale piu', il codice non si riusa.
    expect((await call('GET', '/api/users/me', undefined, student.json.token)).status).toBe(401);
    expect((await call('POST', '/api/auth/reset-password', { username: 'giulia', code: reset.json.code, newPassword: 'altrapass1' })).status).toBe(400);
  });

  it('un account eliminato non entra piu\' con il token vecchio', async () => {
    const { json } = await register('anna');
    expect((await call('DELETE', '/api/users/me', { password: 'password123' }, json.token)).status).toBe(200);
    expect((await call('GET', '/api/calendar', undefined, json.token)).status).toBe(401);
  });
});

describe('calendario', () => {
  it('piu\' verifiche lo stesso giorno, avviso solo per lo stesso evento', async () => {
    const { json } = await register('paolo');
    const t = json.token;
    const ev = (title: string, extra = {}) =>
      call('POST', '/api/calendar', { title, eventDate: '2026-10-12', category: 'VERIFICA', ...extra }, t);
    expect((await ev('Verifica di matematica')).status).toBe(201);
    expect((await ev('Verifica di inglese')).status).toBe(201);
    const dup = await ev('  verifica di MATEMATICA ');
    expect(dup.status).toBe(200);
    expect(dup.json.warning).toBeTruthy();
    expect((await ev('Verifica di matematica', { force: true })).status).toBe(201);
    const list = await call('GET', '/api/calendar', undefined, t);
    expect(list.json.events).toHaveLength(3);
  });

  it('gli eventi per persone specifiche li vedono solo loro e chi li ha creati', async () => {
    const a = await register('autore');
    const b = await register('destinatario');
    const c = await register('estraneo');
    const created = await call(
      'POST',
      '/api/calendar',
      { title: 'Interrogazione', eventDate: '2026-10-13', category: 'INTERROGAZIONE', visibleToUserIds: [b.json.user.id] },
      a.json.token
    );
    expect(created.status).toBe(201);
    const count = async (token: string) => (await call('GET', '/api/calendar', undefined, token)).json.events.length;
    expect(await count(a.json.token)).toBe(1);
    expect(await count(b.json.token)).toBe(1);
    expect(await count(c.json.token)).toBe(0);
  });
});

describe('analisi delle circolari', () => {
  it('un telefono non sovrascrive il riassunto del server', async () => {
    const { json } = await register('lettore');
    await env.DB.prepare(
      "INSERT INTO circulars (number, title, publish_date, r2_pdf_key) VALUES (7, 'Uscita', '2026-10-01', 'circulars/7.pdf')"
    ).run();
    const analysis = { badge: 'RELEVANT', summary: 'Server', deadlines: [], isFallback: false, modelLabel: 'Google Gemini (x)' };
    expect(await upsertAnalysis(env, 7, { ...analysis, perClass: {} }, 2, null)).toBe(true);

    const put = await call('PUT', '/api/circulars/7/analysis', { ...analysis, summary: 'Telefono' }, json.token);
    expect(put.json.stored).toBe(false);
    expect(put.json.current.summary).toBe('Server');

    // Fra telefoni, allo stesso livello, vince l'ultimo; il server resta sopra a tutti.
    await env.DB.prepare('DELETE FROM circular_ai_analysis').run();
    expect((await call('PUT', '/api/circulars/7/analysis', { ...analysis, summary: 'Uno' }, json.token)).json.stored).toBe(true);
    expect((await call('PUT', '/api/circulars/7/analysis', { ...analysis, summary: 'Due' }, json.token)).json.stored).toBe(true);
    expect(await upsertAnalysis(env, 7, { ...analysis, summary: 'Server' }, 2, null)).toBe(true);
  });

  it('rifiuta riassunti enormi', async () => {
    const { json } = await register('spam');
    await env.DB.prepare(
      "INSERT INTO circulars (number, title, publish_date, r2_pdf_key) VALUES (8, 'X', '2026-10-01', 'circulars/8.pdf')"
    ).run();
    const res = await call(
      'PUT',
      '/api/circulars/8/analysis',
      { badge: 'RELEVANT', summary: 'x'.repeat(5000), deadlines: [], isFallback: false, modelLabel: 'AI locale (x)' },
      json.token
    );
    expect(res.status).toBe(400);
  });
});

describe('sondaggi interrogazioni', () => {
  it('calcolato il calendario non si ricalcola e non si ritira l\'invio', async () => {
    const rep = await register('prof', { representativeCode: 'REP-CODE' });
    const s = await register('alunno');
    const created = await call(
      'POST',
      '/api/polls',
      { subject: 'Storia', slots: [{ slotDate: '2026-10-20', capacity: 1 }, { slotDate: '2026-10-21', capacity: 1 }] },
      rep.json.token
    );
    expect(created.status).toBe(201);
    const id = created.json.id;
    expect((await call('PUT', `/api/polls/${id}/publish`, {}, rep.json.token)).status).toBe(200);
    expect((await call('POST', `/api/polls/${id}/submit`, {}, s.json.token)).status).toBe(200);
    expect((await call('POST', `/api/polls/${id}/assignments/run?force=1`, {}, rep.json.token)).status).toBe(200);

    expect((await call('POST', `/api/polls/${id}/assignments/run?force=1`, {}, rep.json.token)).status).toBe(409);
    expect((await call('DELETE', `/api/polls/${id}/submit`, undefined, s.json.token)).status).toBe(409);
  });
});

describe('funzioni di supporto', () => {
  it('la matrice delle preferenze non dice chi ha votato cosa', () => {
    const out = anonymizePairs([
      { from_student_id: 'b', to_student_id: 'a', score: -2 },
      { from_student_id: 'a', to_student_id: 'b', score: 1 },
      { from_student_id: 'c', to_student_id: 'a', score: 2 },
    ]);
    // Coppia a-b: il voto piu' basso sempre da "a" (id minore), qualunque sia il vero autore.
    expect(out).toContainEqual({ from: 'a', to: 'b', score: -2 });
    expect(out).toContainEqual({ from: 'b', to: 'a', score: 1 });
    // Coppia a-c con un voto solo: resta un voto solo, sempre nella stessa direzione.
    expect(out).toContainEqual({ from: 'c', to: 'a', score: 2 });
    expect(out).toHaveLength(3);
  });

  it('titoli confrontati senza maiuscole, accenti e spazi', () => {
    expect(normalizeTitle(' Verifica di Città ')).toBe(normalizeTitle('verifica di citta'));
  });

  it('riconosce i token FCM morti', () => {
    expect(isDeadToken(404, '')).toBe(true);
    expect(isDeadToken(400, '{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}')).toBe(true);
    expect(isDeadToken(400, 'Invalid JSON payload')).toBe(false);
    expect(isDeadToken(500, '')).toBe(false);
  });

  it('la data di oggi e\' quella italiana', () => {
    expect(italianToday(new Date('2026-10-12T23:30:00Z'))).toBe('2026-10-13');
    expect(italianToday(new Date('2026-12-01T10:00:00Z'))).toBe('2026-12-01');
  });
});

describe('elenco classi', () => {
  it('nasconde le classi che non hanno il formato anno + sezione', async () => {
    await register('primo');
    await env.DB.prepare("INSERT OR IGNORE INTO classes (id, label) VALUES ('X', 'DEFAULT_CLASS')").run();
    const r = await call('GET', '/api/auth/classes');
    const labels = (r.json.classes as { label: string }[]).map((c) => c.label);
    expect(labels).toContain('3 B');
    expect(labels).not.toContain('DEFAULT_CLASS');
  });
});
