import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { expect, it } from 'vitest';

it('migra materiali e circolari nella prima gita senza perdere versioni o segnalazioni', () => {
  const db = new DatabaseSync(':memory:');
  try {
    db.exec(`PRAGMA foreign_keys = ON;
      CREATE TABLE classes (id TEXT PRIMARY KEY);
      CREATE TABLE circulars (number INTEGER PRIMARY KEY);
      INSERT INTO classes VALUES ('classe');
      INSERT INTO circulars VALUES (123);
    `);
    db.exec(readFileSync(new URL('../migrations/018_gita.sql', import.meta.url), 'utf8'));
    db.exec(readFileSync(new URL('../migrations/019_gita_circulars.sql', import.meta.url), 'utf8'));
    db.exec(`INSERT INTO gita_items (id, class_id, kind, title, created_by) VALUES ('doc', 'classe', 'DOCUMENT', 'Programma', 'rep');
      INSERT INTO gita_versions (id, item_id, version_no, uploaded_by, file_r2_key) VALUES ('v1', 'doc', 1, 'rep', 'file.pdf');
      INSERT INTO gita_reports (id, class_id, item_id, reported_by, reason) VALUES ('report', 'classe', 'doc', 'studente', 'Orario');
      INSERT INTO gita_circulars (class_id, circular_number, added_by) VALUES ('classe', 123, 'rep');`);
    db.exec(readFileSync(new URL('../migrations/032_gita_trips.sql', import.meta.url), 'utf8'));
    expect(db.prepare('SELECT name FROM gita_trips').get()?.name).toBe('Gita 1');
    expect(db.prepare('SELECT trip_id FROM gita_items').get()?.trip_id).toBe('default:classe');
    expect(db.prepare('SELECT trip_id FROM gita_circulars').get()?.trip_id).toBe('default:classe');
    expect(db.prepare('SELECT file_r2_key FROM gita_versions').get()?.file_r2_key).toBe('file.pdf');
    expect(db.prepare('SELECT reason FROM gita_reports').get()?.reason).toBe('Orario');
    expect(db.prepare('PRAGMA foreign_key_check').all()).toEqual([]);
  } finally { db.close(); }
});
