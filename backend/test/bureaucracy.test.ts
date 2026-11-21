import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { signJWT } from '../src/auth';
import worker from '../src/index';
import type { Env, UserRole } from '../src/types';
import { createD1 } from './d1shim';

it('isolates class deliveries, permits concurrent practices and freezes the archive', async () => {
  const { db, d1 } = createD1();
  const env = { DB: d1, JWT_SECRET: 'bureaucracy-test' } as Env;
  const ctx = {} as ExecutionContext;
  db.exec('DROP TABLE bureaucracy_deliveries; DROP TABLE bureaucracy_practices;');
  const migration = readFileSync(new URL('../migrations/034_bureaucracy.sql', import.meta.url), 'utf8');
  db.exec(migration);
  db.exec(migration);
  db.exec(`DROP TRIGGER seat_rotation_history_insert;
    DROP TRIGGER seat_rotation_history_update;
    DROP TRIGGER seat_rotation_history_delete;
    ALTER TABLE classes DROP COLUMN seat_row_cycle;
    ALTER TABLE classes DROP COLUMN seat_companion_wait;
    ALTER TABLE classes DROP COLUMN seat_rotation_revision;`);
  db.exec(readFileSync(new URL('../migrations/035_seat_rotation_and_paperwork_deadlines.sql', import.meta.url), 'utf8'));
  db.exec("INSERT INTO classes (id, label) VALUES ('class-b', '5 B')");

  async function addUser(id: string, role: UserRole = 'STUDENT', classId = 'DEFAULT_CLASS') {
    db.prepare('INSERT INTO users (id, username, first_name, last_name, password_hash, role, class_id) VALUES (?, ?, ?, ?, ?, ?, ?)')
      .run(id, id, id, 'Test', 'unused', role, classId);
    return signJWT({ sub: id, username: id, role, classId }, env.JWT_SECRET);
  }

  async function call(token: string, method = 'GET', path = '', data?: unknown) {
    const response = await worker.fetch(new Request('https://api.test/api/burocrazia' + path, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: data === undefined ? undefined : JSON.stringify(data)
    }), env, ctx);
    return { status: response.status, data: await response.json(), cache: response.headers.get('Cache-Control') };
  }

  const rep = await addUser('rep', 'REPRESENTATIVE');
  const student = await addUser('studente');
  const peer = await addUser('compagno');
  const outsider = await addUser('other', 'REPRESENTATIVE', 'class-b');
  expect((await call(student, 'POST', '', { title: 'Ricevuta' })).status).toBe(403);
  expect((await call(rep, 'POST', '', { title: '   ' })).status).toBe(400);
  const first = await call(rep, 'POST', '', { title: ' Ricevuta ', description: ' Porta la stampa. ', dueDate: '2026-12-07' });
  const second = await call(rep, 'POST', '', { title: 'Circolare firmata' });
  expect(first.status).toBe(201);
  expect(second.status).toBe(201);
  const id = (first.data as { id: string }).id;
  const deliveries = '/' + id + '/deliveries';
  const archive = '/' + id + '/archive';
  const deadline = '/' + id + '/deadline';
  expect((await call(rep)).data).toMatchObject({ practices: expect.arrayContaining([
    expect.objectContaining({ id, dueDate: '2026-12-07' })
  ]) });
  expect((await call(student, 'PUT', deadline, { dueDate: null })).status).toBe(403);
  expect((await call(outsider, 'PUT', deadline, { dueDate: null })).status).toBe(404);
  expect((await call(rep, 'PUT', deadline, { dueDate: '2026-02-29' })).status).toBe(400);
  expect((await call(rep, 'PUT', deadline, { dueDate: null })).status).toBe(200);
  expect((await call(rep, 'PUT', deadline, { dueDate: '2026-12-09' })).status).toBe(200);
  expect((await call(student, 'GET', deliveries)).status).toBe(403);
  expect((await call(student, 'PUT', archive)).status).toBe(403);
  expect((await call(outsider, 'GET', deliveries)).status).toBe(404);
  expect((await call(outsider, 'PUT', archive)).status).toBe(404);
  expect((await call(rep, 'PUT', deliveries + '/other', { received: true })).status).toBe(404);
  expect((await call(rep, 'PUT', deliveries + '/studente', { received: 'true' })).status).toBe(400);
  expect((await call(rep, 'PUT', deliveries + '/studente', { received: true })).status).toBe(200);
  expect((await call(rep, 'PUT', deliveries + '/studente', { received: true })).status).toBe(200);

  const own = await call(student);
  expect(own.cache).toBe('no-store');
  expect(own.data).toEqual({
    canManage: false,
    practices: expect.arrayContaining([
      { id, title: 'Ricevuta', description: 'Porta la stampa.', dueDate: '2026-12-09', archivedAt: null, received: true },
      expect.objectContaining({ title: 'Circolare firmata', dueDate: null, received: false, archivedAt: null })
    ])
  });
  expect((await call(peer)).data).toMatchObject({ practices: expect.arrayContaining([expect.objectContaining({ id, received: false })]) });
  expect((await call(outsider)).data).toEqual({ canManage: true, practices: [] });

  expect((await call(rep, 'PUT', deliveries + '/studente', { received: false })).status).toBe(200);
  expect((await call(rep, 'PUT', deliveries + '/studente', { received: true })).status).toBe(200);
  expect((await call(rep, 'PUT', archive)).status).toBe(200);
  expect((await call(rep, 'PUT', deadline, { dueDate: null })).status).toBe(404);
  const archived = db.prepare('SELECT archived_at FROM bureaucracy_practices WHERE id = ?').get(id);
  expect(archived?.archived_at).toEqual(expect.any(String));
  expect((await call(rep, 'PUT', archive)).status).toBe(200);
  expect(db.prepare('SELECT archived_at FROM bureaucracy_practices WHERE id = ?').get(id)).toEqual(archived);
  expect((await call(rep, 'PUT', deliveries + '/studente', { received: false })).status).toBe(409);

  const newcomer = await addUser('nuovo');
  expect((await call(newcomer)).data).toMatchObject({ practices: [{ title: 'Circolare firmata', archivedAt: null }] });
  expect((await call(rep, 'GET', deliveries)).data).toEqual({
    deliveries: [
      { studentId: 'compagno', name: 'Compagno Test', received: false },
      { studentId: 'rep', name: 'Rep Test', received: false },
      { studentId: 'studente', name: 'Studente Test', received: true }
    ]
  });
  expect((await call(student)).data).toMatchObject({
    practices: expect.arrayContaining([expect.objectContaining({ id, archivedAt: expect.any(String), received: true })])
  });
});
