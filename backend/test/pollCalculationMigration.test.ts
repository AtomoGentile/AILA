import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { createD1 } from './d1shim';

it('la migrazione conserva risultati e bonus precedenti senza inventare una fotografia storica', () => {
  const { db } = createD1();
  try {
    db.exec("DROP TRIGGER redact_interrogation_calculations; DROP TABLE interrogation_swap_events; DROP TABLE interrogation_calculations;");
    db.exec(`INSERT INTO users(id,first_name,last_name,username,password_hash,role) VALUES('u','A','B','ab','x','STUDENT');
      INSERT INTO interrogation_grids(id,subject) VALUES('g','Biologia');
      INSERT INTO interrogation_slots(id,grid_id,slot_date,capacity) VALUES('s','g','2026-12-03',1);
      INSERT INTO interrogation_votes(slot_id,student_id,vote_score) VALUES('s','u',0);
      INSERT INTO student_sacrifice_bonus VALUES('u','Biologia',70);
      INSERT INTO interrogation_assignments(id,slot_id,student_id) VALUES('a','s','u');`);
    const before = db.prepare('SELECT * FROM interrogation_assignments').all();
    const migration = readFileSync(new URL('../migrations/026_poll_calculations.sql', import.meta.url), 'utf8');
    db.exec(migration);
    db.exec(migration); // Additiva e ripetibile.
    expect(db.prepare('SELECT * FROM interrogation_assignments').all()).toEqual(before);
    expect(db.prepare('SELECT bonus_points FROM student_sacrifice_bonus').get()?.bonus_points).toBe(70);
    expect(db.prepare('SELECT algorithm_version, input_json, result_json FROM interrogation_calculations').get())
      .toMatchObject({ algorithm_version: 'legacy', input_json: null, result_json: null });
    db.exec("DELETE FROM users WHERE id='u'");
    expect(db.prepare('SELECT COUNT(*) AS n FROM interrogation_assignments').get()?.n).toBe(0);
    expect(db.prepare('SELECT COUNT(*) AS n FROM interrogation_calculations').get()?.n).toBe(1);
    expect(db.prepare('PRAGMA foreign_key_check').all()).toEqual([]);
  } finally { db.close(); }
});
