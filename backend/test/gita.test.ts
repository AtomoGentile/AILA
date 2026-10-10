// Gita: rotte vere su D1 finto (test/d1shim.ts) e un R2 finto in memoria.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { createD1 } from './d1shim';
import type { Env } from '../src/types';

const notified = vi.hoisted(() => [] as { userIds: string[]; title: string }[]);
vi.mock('../src/services/fcm', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../src/services/fcm')>()),
  notifyUsers: async (_env: unknown, userIds: string[], title: string) => {
    notified.push({ userIds, title });
  },
}));

const ctx = { waitUntil() {}, passThroughOnException() {} } as never;
const ADMIN = 'admin-secret-di-prova-1234';
let env: Env;
let r2: Map<string, Uint8Array>;

function fakeR2() {
  r2 = new Map();
  return {
    async put(key: string, body: unknown) {
      let bytes: Uint8Array;
      if (typeof body === 'string') bytes = new TextEncoder().encode(body);
      else bytes = new Uint8Array(await new Response(body as ReadableStream).arrayBuffer());
      r2.set(key, bytes);
    },
    async get(key: string) {
      const bytes = r2.get(key);
      if (!bytes) return null;
      return { body: new Response(bytes).body, text: async () => new TextDecoder().decode(bytes) };
    },
    async head(key: string) {
      return r2.has(key) ? {} : null;
    },
  };
}

beforeEach(() => {
  const { d1 } = createD1();
  env = { DB: d1, CIRCULARS_BUCKET: fakeR2() as unknown as R2Bucket, JWT_SECRET: 'test-secret', ADMIN_SECRET: ADMIN } as unknown as Env;
  notified.length = 0;
});

async function call(method: string, path: string, body?: unknown, token?: string, headers: Record<string, string> = {}) {
  const isForm = body instanceof FormData;
  const res = await worker.fetch(
    new Request(`https://api.test${path}`, {
      method,
      headers: {
        ...(body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    }),
    env,
    ctx
  );
  const json = (await res.json().catch(() => ({}))) as Record<string, any>;
  return { status: res.status, json, res };
}

async function repCode(classLabel = '3 B') {
  const res = await call('POST', '/api/admin/representative-invites', { classLabel }, undefined, { 'X-Admin-Secret': ADMIN });
  return res.json.codes[0].code as string;
}

function register(username: string, extra: Record<string, unknown> = {}) {
  return call('POST', '/api/auth/register', {
    firstName: 'Nome', lastName: username, username, password: 'password123', heightCm: 170, classLabel: '3 B', ...extra,
  });
}

/** Rappresentante e studente della stessa classe, più uno studente di un'altra classe. */
async function cast() {
  const rep = await register('rep', { representativeCode: await repCode() });
  const classCode = (await call('GET', '/api/users/class-code', undefined, rep.json.token)).json.code;
  const student = await register('studente', { classCode });
  const other = await register('altra', { classLabel: '5 A' });
  return { rep: rep.json.token as string, student: student.json.token as string, other: other.json.token as string };
}

function pdf(name = 'programma.pdf', text = 'Partenza ore 7:30') {
  return new File([new Uint8Array([0x25, 0x50, 0x44, 0x46, ...new TextEncoder().encode(text)])], name, { type: 'application/pdf' });
}

function documentForm(file: File, extra: Record<string, string> = {}) {
  const form = new FormData();
  form.append('title', 'Programma gita');
  form.append('category', 'PROGRAMMA');
  form.append('file', file);
  form.append('text', 'Partenza ore 7:30 da piazza. Rientro ore 19:00. Quota 85 euro, saldo entro il 10 novembre.');
  for (const [k, v] of Object.entries(extra)) form.append(k, v);
  return form;
}

describe('gita: permessi e lettura', () => {
  it('solo il Rappresentante crea voci; tutta la classe le legge', async () => {
    const { rep, student } = await cast();
    expect((await call('POST', '/api/gita/items', documentForm(pdf()), student)).status).toBe(403);
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    expect(created.status).toBe(201);

    const list = await call('GET', '/api/gita', undefined, student);
    expect(list.status).toBe(200);
    expect(list.json.canEdit).toBe(false);
    expect(list.json.items).toHaveLength(1);
    expect(list.json.items[0].title).toBe('Programma gita');
    expect(list.json.items[0].current.versionNo).toBe(1);
    expect(list.json.items[0].current.uploadedAt).toBeTruthy();
  });

  it('una classe diversa non vede né scarica nulla', async () => {
    const { rep, other } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const id = created.json.id;
    expect((await call('GET', '/api/gita', undefined, other)).json.items).toHaveLength(0);
    expect((await call('GET', `/api/gita/versions/${created.json.version.id}/file`, undefined, other)).status).toBe(404);
    expect((await call('POST', `/api/gita/items/${id}/reports`, { reason: 'x' }, other)).status).toBe(404);
  });

  it('il PDF si scarica solo con login, e solo se è un PDF', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const versionId = created.json.version.id;
    const anon = await worker.fetch(new Request(`https://api.test/api/gita/versions/${versionId}/file`), env, ctx);
    expect(anon.status).toBe(401);
    const file = await worker.fetch(new Request(`https://api.test/api/gita/versions/${versionId}/file`, {
      headers: { Authorization: `Bearer ${student}` },
    }), env, ctx);
    expect(file.status).toBe(200);
    expect(file.headers.get('Content-Type')).toBe('application/pdf');
    expect(await file.text()).toContain('Partenza');

    const notPdf = new File(['ciao'], 'nota.txt', { type: 'text/plain' });
    expect((await call('POST', '/api/gita/items', documentForm(notPdf), rep)).status).toBe(400);
  });
});

describe('gita: storico versioni', () => {
  it('sostituire un documento conserva la versione precedente e mostra la data', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf('v1.pdf', 'uno')), rep);
    const id = created.json.id;
    const replaced = await call('POST', `/api/gita/items/${id}/versions`, documentForm(pdf('v2.pdf', 'due')), rep);
    expect(replaced.status).toBe(201);

    const history = await call('GET', `/api/gita/items/${id}/versions`, undefined, student);
    expect(history.status).toBe(200);
    expect(history.json.versions.map((v: any) => v.versionNo)).toEqual([2, 1]);
    expect(history.json.versions.every((v: any) => v.uploadedAt)).toBe(true);

    const list = await call('GET', '/api/gita', undefined, student);
    expect(list.json.items[0].current.versionNo).toBe(2);

    // La versione 1 resta scaricabile.
    const oldId = history.json.versions[1].id;
    const old = await worker.fetch(new Request(`https://api.test/api/gita/versions/${oldId}/file`, {
      headers: { Authorization: `Bearer ${student}` },
    }), env, ctx);
    expect(await old.text()).toContain('uno');
  });

  it('ritirare una voce la nasconde a tutti ma lo storico resta al Rappresentante', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const id = created.json.id;
    expect((await call('DELETE', `/api/gita/items/${id}`, undefined, student)).status).toBe(403);
    expect((await call('DELETE', `/api/gita/items/${id}`, undefined, rep)).status).toBe(200);
    expect((await call('GET', '/api/gita', undefined, student)).json.items).toHaveLength(0);
    expect((await call('GET', `/api/gita/items/${id}/versions`, undefined, student)).status).toBe(404);
    expect((await call('GET', `/api/gita/items/${id}/versions`, undefined, rep)).json.versions).toHaveLength(1);
  });
});

describe('gita: link', () => {
  it('accetta solo http/https, e ogni modifica è una versione', async () => {
    const { rep } = await cast();
    expect((await call('POST', '/api/gita/items', { title: 'Sito', url: 'javascript:alert(1)' }, rep)).status).toBe(400);
    const created = await call('POST', '/api/gita/items', { title: 'Sito agenzia', url: 'https://agenzia.example/gita' }, rep);
    expect(created.status).toBe(201);
    const id = created.json.id;
    const again = await call('POST', `/api/gita/items/${id}/versions`, { url: 'https://agenzia.example/gita-2' }, rep);
    expect(again.status).toBe(201);
    const history = await call('GET', `/api/gita/items/${id}/versions`, undefined, rep);
    expect(history.json.versions.map((v: any) => v.url)).toEqual([
      'https://agenzia.example/gita-2',
      'https://agenzia.example/gita',
    ]);
  });
});

describe('gita: segnalazioni', () => {
  it('chiunque segnala; il segnalante vede lo stato e viene avvisato quando è risolta', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const id = created.json.id;

    const report = await call('POST', `/api/gita/items/${id}/reports`, { reason: 'La quota è diversa dal preventivo' }, student);
    expect(report.status).toBe(201);
    expect(notified.some((n) => n.title.includes('segnalazione'))).toBe(true);

    const mine = await call('GET', '/api/gita', undefined, student);
    expect(mine.json.reports[0].status).toBe('OPEN');

    expect((await call('PATCH', `/api/gita/reports/${report.json.id}`, { status: 'RESOLVED' }, student)).status).toBe(403);
    expect((await call('PATCH', `/api/gita/reports/${report.json.id}`, { status: 'RESOLVED' }, rep)).status).toBe(200);

    const after = await call('GET', '/api/gita', undefined, student);
    expect(after.json.reports[0].status).toBe('RESOLVED');
    expect(notified.some((n) => n.title.includes('risolta'))).toBe(true);
  });

  it('il Rappresentante vede tutte le segnalazioni della classe, lo studente solo le sue', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const id = created.json.id;
    const other = await register('secondo', { classCode: (await call('GET', '/api/users/class-code', undefined, rep)).json.code });
    await call('POST', `/api/gita/items/${id}/reports`, { reason: 'Data sbagliata' }, student);
    await call('POST', `/api/gita/items/${id}/reports`, { reason: 'Orario sbagliato' }, other.json.token);
    expect((await call('GET', '/api/gita', undefined, rep)).json.reports).toHaveLength(2);
    expect((await call('GET', '/api/gita', undefined, student)).json.reports).toHaveLength(1);
  });
});

describe('gita: materiale per l\'assistente', () => {
  it('il corpus contiene la versione corrente con il testo estratto', async () => {
    const { rep, student } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    await call('POST', `/api/gita/items/${created.json.id}/versions`, documentForm(pdf('v2.pdf'), { text: 'Partenza ore 8:00' }), rep);
    const corpus = await call('GET', '/api/gita/corpus', undefined, student);
    expect(corpus.status).toBe(200);
    expect(corpus.json.documents).toHaveLength(1);
    expect(corpus.json.documents[0].versionNo).toBe(2);
    expect(corpus.json.documents[0].text).toBe('Partenza ore 8:00');
  });
});

describe('gita: date', () => {
  it('le date sono nell\'ora italiana, non in UTC', async () => {
    const { rep } = await cast();
    const created = await call('POST', '/api/gita/items', documentForm(pdf()), rep);
    const expected = new Date().toLocaleString('sv-SE', { timeZone: 'Europe/Rome' }).slice(0, 10);
    expect(created.json.version.uploadedAt.slice(0, 10)).toBe(expected);
    expect(created.json.version.uploadedAt).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
  });
});
