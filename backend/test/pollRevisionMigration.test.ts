import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { createD1 } from './d1shim';

it('la migrazione delle revisioni conserva eventi, assegnazioni e bonus esistenti', () => {
  const { db } = createD1();
  try {
    db.exec(`DROP TRIGGER redact_interrogation_revisions; DROP TABLE interrogation_revisions;
      DROP INDEX idx_calendar_poll_grid; ALTER TABLE calendar_events DROP COLUMN poll_grid_id;
      INSERT INTO users(id,first_name,last_name,username,password_hash,role) VALUES('u','A','B','ab','x','STUDENT');
      INSERT INTO interrogation_grids(id,subject) VALUES('g','Biologia');
      INSERT INTO interrogation_slots(id,grid_id,slot_date,capacity) VALUES('s','g','2026-12-03',1);
      INSERT INTO interrogation_assignments(id,slot_id,student_id) VALUES('a','s','u');
      INSERT INTO student_sacrifice_bonus VALUES('u','Biologia',70);
      INSERT INTO calendar_events(id,title,event_date,category) VALUES('e','Interrogazione','2026-12-03','INTERROGAZIONE');`);
    const assignments = db.prepare('SELECT * FROM interrogation_assignments').all();
    db.exec(readFileSync(new URL('../migrations/027_poll_revisions.sql', import.meta.url), 'utf8'));
    expect(db.prepare('SELECT * FROM interrogation_assignments').all()).toEqual(assignments);
    expect(db.prepare('SELECT bonus_points FROM student_sacrifice_bonus').get()?.bonus_points).toBe(70);
    expect(db.prepare('SELECT id,poll_grid_id FROM calendar_events').get()).toMatchObject({ id: 'e', poll_grid_id: null });
    expect(db.prepare('SELECT COUNT(*) AS n FROM interrogation_revisions').get()?.n).toBe(0);
    expect(db.prepare('PRAGMA foreign_key_check').all()).toEqual([]);
  } finally { db.close(); }
});
