// Rotte vere su un D1 finto (test/d1shim.ts) con lo schema reale.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import { anonymizePairs } from '../src/routes/preferences';
import { normalizeTitle } from '../src/routes/calendar';
import { isDeadToken } from '../src/services/fcm';
import { italianToday, upsertAnalysis } from '../src/services/summarizer';
import type { Env } from '../src/types';

// Le notifiche si registrano invece di partire: servono a controllare chi viene avvisato.
const notified = vi.hoisted(() => [] as { userIds: string[]; title: string; data?: Record<string, string> }[]);
vi.mock('../src/services/fcm', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../src/services/fcm')>()),
  notifyUsers: async (_env: unknown, userIds: string[], title: string, _body: string, data?: Record<string, string>) => {
    notified.push({ userIds, title, data });
  },
}));

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
let env: Env;

beforeEach(() => {
  const { d1 } = createD1();
  env = { DB: d1, JWT_SECRET: 'test-secret', ADMIN_SECRET: ADMIN } as unknown as Env;
  notified.length = 0;
});

const ADMIN = 'admin-secret-di-prova-1234';

/** Nuovo codice Rappresentante per la classe, emesso dalla rotta di amministrazione. */
async function repCode(classLabel = '3 B', ttlDays?: number): Promise<string> {
  const res = await call('POST', '/api/admin/representative-invites', { classLabel, ttlDays }, undefined, { 'X-Admin-Secret': ADMIN });
  expect(res.status).toBe(201);
  return res.json.codes[0].code;
}

/** Codice della classe del Rappresentante (serve a chi si registra dopo di lui). */
async function classCodeOf(repToken: string): Promise<string> {
  return (await call('GET', '/api/users/class-code', undefined, repToken)).json.code;
}

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

    const rep = await register('rappresentante', { representativeCode: await repCode() });
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
    expect((await register('rep1', { representativeCode: await repCode() })).status).toBe(201);
    // Il secondo non deve chiedere il codice classe al primo: il suo codice vale gia' solo qui.
    expect((await register('rep2', { representativeCode: await repCode() })).status).toBe(201);
    expect((await register('rep3', { representativeCode: await repCode() })).status).toBe(409);
  });

  it('con un Rappresentante in classe il codice serve anche se lui non ha mai aperto la Scheda Classe', async () => {
    expect((await register('rep1', { representativeCode: await repCode() })).status).toBe(201);
    const stranger = await register('sconosciuto');
    expect(stranger.status).toBe(403);
    expect(stranger.json.classCodeRequired).toBe(true);
  });
});

describe('codici Rappresentante per classe e monouso', () => {
  it('un codice di un\'altra classe viene rifiutato', async () => {
    const other = await repCode('5 A');
    const res = await register('estraneo', { representativeCode: other });
    expect(res.status).toBe(400);
    expect(res.json.error).toMatch(/altra classe/);
    // Nella sua classe vale.
    expect((await register('estraneo', { representativeCode: other, classLabel: '5 A' })).status).toBe(201);
  });

  it('un codice gia\' usato viene rifiutato', async () => {
    const code = await repCode();
    const first = await register('rep1', { representativeCode: code });
    expect(first.status).toBe(201);
    expect(first.json.user.role).toBe('REPRESENTATIVE');
    const again = await register('rep2', { representativeCode: code });
    expect(again.status).toBe(400);
    expect(again.json.error).toMatch(/già usato/);
  });

  it('un codice scaduto viene rifiutato', async () => {
    const code = await repCode();
    await env.DB.prepare('UPDATE representative_invites SET expires_at = ?').bind(Math.floor(Date.now() / 1000) - 1).run();
    const res = await register('rep1', { representativeCode: code });
    expect(res.status).toBe(400);
    expect(res.json.error).toMatch(/scaduto/);
  });

  it('un codice inventato viene rifiutato, minuscole e trattini non contano', async () => {
    expect((await register('rep1', { representativeCode: 'AAAAA-AAAAA' })).status).toBe(400);
    const code = await repCode();
    const ok = await register('rep1', { representativeCode: ` ${code.toLowerCase().replace('-', ' ')} ` });
    expect(ok.status).toBe(201);
    expect(ok.json.user.role).toBe('REPRESENTATIVE');
  });

  it('se la registrazione fallisce il codice non si consuma', async () => {
    await register('preso', { classLabel: '5 A' });
    const code = await repCode();
    expect((await register('preso', { representativeCode: code })).status).toBe(409);
    expect((await register('libero', { representativeCode: code })).status).toBe(201);
  });

  it('solo con il segreto di amministrazione, mai con un token dell\'app', async () => {
    const rep = await register('rep1', { representativeCode: await repCode() });
    const issue = (headers: Record<string, string>, token?: string) =>
      call('POST', '/api/admin/representative-invites', { classLabel: '3 B' }, token, headers);
    expect((await issue({}, rep.json.token)).status).toBe(401);
    expect((await issue({ 'X-Admin-Secret': 'sbagliato' })).status).toBe(401);
    expect((await issue({ 'X-Admin-Secret': ADMIN })).status).toBe(201);
    expect((await call('POST', '/api/admin/representative-invites', { classLabel: '3 B', ttlDays: 90 }, undefined, { 'X-Admin-Secret': ADMIN })).status).toBe(400);

    env = { ...env, ADMIN_SECRET: undefined } as Env;
    expect((await issue({ 'X-Admin-Secret': '' })).status).toBe(503);
  });

  it('count 2: due codici diversi, uno per Rappresentante', async () => {
    const headers = { 'X-Admin-Secret': ADMIN };
    const res = await call('POST', '/api/admin/representative-invites', { classLabel: '3 B', count: 2 }, undefined, headers);
    expect(res.status).toBe(201);
    const [a, b] = res.json.codes.map((c: { code: string }) => c.code);
    expect(a).toMatch(/^[A-Z2-9]{5}-[A-Z2-9]{5}$/);
    expect(a).not.toBe(b);
    expect((await register('rep1', { representativeCode: a })).status).toBe(201);
    expect((await register('rep2', { representativeCode: b })).status).toBe(201);
    expect((await call('POST', '/api/admin/representative-invites', { classLabel: '3 B', count: 3 }, undefined, headers)).status).toBe(400);
  });

  it('la pagina di amministrazione non contiene segreti e non si mette in cache', async () => {
    const res = await worker.fetch(new Request('https://api.test/admin'), env, ctx);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain('Codici Rappresentante');
    expect(html).not.toContain(ADMIN);
    expect(res.headers.get('Cache-Control')).toBe('no-store');
    const csp = res.headers.get('Content-Security-Policy') ?? '';
    expect(csp).toContain("frame-ancestors 'none'");
    const nonce = /script-src 'nonce-([a-f0-9]+)'/.exec(csp)?.[1];
    expect(nonce && html.includes(`<script nonce="${nonce}">`)).toBe(true);
  });

  it('elenco senza codici in chiaro e ritiro di un codice non usato', async () => {
    const headers = { 'X-Admin-Secret': ADMIN };
    const used = await repCode();
    await register('rep1', { representativeCode: used });
    const spare = (await call('POST', '/api/admin/representative-invites', { classLabel: '3B' }, undefined, headers)).json.codes[0];

    const list = await call('GET', '/api/admin/representative-invites?classLabel=3%20B', undefined, undefined, headers);
    expect(list.status).toBe(200);
    expect(list.json.invites).toHaveLength(2);
    expect(JSON.stringify(list.json)).not.toContain(spare.code);
    expect(list.json.invites.map((i: any) => i.usedBy)).toContain('rep1');

    expect((await call('DELETE', `/api/admin/representative-invites/${spare.id}`, undefined, undefined, headers)).status).toBe(200);
    expect((await register('rep2', { representativeCode: spare.code })).status).toBe(400);
  });

  it('avvisa i Rappresentanti e la Guardia della classe quando ne arriva uno nuovo', async () => {
    const rep1 = await register('rep1', { representativeCode: await repCode() });
    const guard = await register('guardia', { classCode: await classCodeOf(rep1.json.token) });
    expect((await call('PUT', '/api/proposals/security-guard', { userId: guard.json.user.id }, rep1.json.token)).status).toBe(200);
    await register('altrove', { classLabel: '5 A', representativeCode: await repCode('5 A') });
    notified.length = 0;

    const rep2 = await register('rep2', { representativeCode: await repCode() });
    expect(rep2.status).toBe(201);
    await new Promise((r) => setTimeout(r, 0));
    const sent = notified.find((n) => n.data?.action === 'representative_joined');
    expect(sent?.userIds.sort()).toEqual([rep1.json.user.id, guard.json.user.id].sort());
  });

  it('codice unico di transizione: solo fino alla data e solo in una classe senza Rappresentanti', async () => {
    env = { ...env, REPRESENTATIVE_SIGNUP_CODE: 'VECCHIO', REPRESENTATIVE_GLOBAL_CODE_UNTIL: '2999-12-31' } as Env;
    const first = await register('rep1', { representativeCode: 'VECCHIO' });
    expect(first.status).toBe(201);
    expect(first.json.user.role).toBe('REPRESENTATIVE');
    // Il secondo, che chiude il quorum, no.
    expect((await register('rep2', { representativeCode: 'VECCHIO' })).status).toBe(400);

    env = { ...env, REPRESENTATIVE_GLOBAL_CODE_UNTIL: '2020-01-01' } as Env;
    expect((await register('rep3', { representativeCode: 'VECCHIO', classLabel: '5 A' })).status).toBe(400);
    env = { ...env, REPRESENTATIVE_GLOBAL_CODE_UNTIL: undefined } as Env;
    expect((await register('rep3', { representativeCode: 'VECCHIO', classLabel: '5 A' })).status).toBe(400);
  });
});

describe('token', () => {
  async function sign(header: object, payload: object) {
    const enc = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
    const data = `${enc(header)}.${enc(payload)}`;
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode('test-secret'), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const sig = Buffer.from(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data))).toString('base64url');
    return `${data}.${sig}`;
  }

  it('rifiuta un token senza scadenza o con un algoritmo diverso, anche se firmato', async () => {
    const { json } = await register('tok');
    const noExp = await sign({ alg: 'HS256', typ: 'JWT' }, { sub: json.user.id, username: 'tok', role: 'STUDENT', iat: 1 });
    expect((await call('GET', '/api/users/me', undefined, noExp)).status).toBe(401);
    const otherAlg = await sign({ alg: 'none', typ: 'JWT' }, { sub: json.user.id, username: 'tok', role: 'STUDENT', iat: 1, exp: 4102444800 });
    expect((await call('GET', '/api/users/me', undefined, otherAlg)).status).toBe(401);
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
    const rep = await register('capo', { representativeCode: await repCode() });
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

  it('un Rappresentante non genera il codice di reset per l\'altro Rappresentante', async () => {
    // Con l'account dell'altro Rappresentante una persona sola avrebbe due firme del quorum
    // (la terza, la Guardia, la nomina lei stessa) e potrebbe svelare gli anonimi da sola.
    const rep1 = await register('rep1', { representativeCode: await repCode() });
    const rep2 = await register('rep2', { representativeCode: await repCode() });
    expect((await call('POST', `/api/users/${rep2.json.user.id}/reset-code`, {}, rep1.json.token)).status).toBe(403);
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
    const rep = await register('prof', { representativeCode: await repCode() });
    const s = await register('alunno', { classCode: await classCodeOf(rep.json.token) });
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

describe('evento AI con rimando alla circolare', () => {
  it('salva il numero solo se la circolare esiste e solo per eventi AI', async () => {
    const u = await register('mario');
    await env.DB.prepare(
      "INSERT INTO circulars (number, title, publish_date, r2_pdf_key) VALUES (42, 'Gita', '2026-09-01', 'k')"
    ).run();
    const create = (extra: Record<string, unknown>, title: string) =>
      call('POST', '/api/calendar', { title, eventDate: '2026-10-10', category: 'AVVISO', ...extra }, u.json.token);

    expect((await create({ isAiGenerated: true, circularNumber: 42 }, 'Uno')).status).toBe(201);
    expect((await create({ isAiGenerated: true, circularNumber: 999 }, 'Due')).status).toBe(201);
    expect((await create({ circularNumber: 42 }, 'Tre')).status).toBe(201);

    const list = await call('GET', '/api/calendar', undefined, u.json.token);
    const byTitle = Object.fromEntries((list.json.events as any[]).map((e) => [e.title, e.circularNumber]));
    expect(byTitle).toEqual({ Uno: 42, Due: null, Tre: null });
  });
});
