import { expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { createD1 } from './d1shim';
import { minimumAssignment, auditRows } from '../scripts/audit-polls.mjs';

it('il confronto esatto rispetta capacità e trova il minimo su tutte le 256 griglie a due persone', () => {
  const students = ['a', 'b'], slots = [{ id: 'x', capacity: 1 }, { id: 'y', capacity: 1 }];
  for (const a of [50, 0, -60, -300]) for (const b of [50, 0, -60, -300])
    for (const c of [50, 0, -60, -300]) for (const d of [50, 0, -60, -300]) {
      const matrix = { a: { x: a, y: b }, b: { x: c, y: d } };
      const result = minimumAssignment(students, slots, (student, slot) => matrix[student][slot.id] < 0 ? 1 : 0);
      expect(result.flow).toBe(2);
      expect(new Set(result.assignments.map(a => a.slotId)).size).toBe(2);
      expect(result.cost).toBe(Math.min(Number(a < 0) + Number(d < 0), Number(b < 0) + Number(c < 0)));
    }
});

it('rileva un rosso evitabile e anonimizza tutti gli identificativi', () => {
  const rows = [
    ['grid', { id: 'grid-secret', subject: 'Storia', createdAt: '2026-10-20' }],
    ...['x', 'y'].map(id => ['slot', { id, gridId: 'grid-secret', date: id, capacity: 1 }]),
    ...[['student-secret-a','x',50],['student-secret-a','y',50],['student-secret-b','x',50],['student-secret-b','y',-60]].map(([studentId,slotId,score]) => ['vote',{studentId,slotId,score}]),
    ['assignment',{studentId:'student-secret-a',slotId:'x'}],
    ['assignment',{studentId:'student-secret-b',slotId:'y'}],
    ['swap',{gridId:'grid-secret'}]
  ].map(([kind,data]) => ({kind,data:JSON.stringify(data)}));
  const report = auditRows(rows);
  expect(report[0]).toMatchObject({ assignedNegative:1, minimumNegative:0, avoidableNegative:1, acceptedSwaps:1 });
  expect(report[0].alternative.map(a=>a.vote)).toEqual([50,50]);
  expect(JSON.stringify(report)).not.toContain('secret');
});

it('la query di produzione è leggibile dallo schema reale e non modifica i dati', () => {
  const {db}=createD1();
  try {
    db.exec("INSERT INTO users(id,first_name,last_name,username,password_hash,role) VALUES('u','A','B','ab','x','STUDENT'); INSERT INTO interrogation_grids(id,subject) VALUES('g','Storia'); INSERT INTO interrogation_slots(id,grid_id,slot_date,capacity) VALUES('s','g','2026-10-20',1); INSERT INTO interrogation_assignments(id,slot_id,student_id) VALUES('a','s','u');");
    const before=db.prepare('SELECT total_changes() AS n').get();
    const rows=db.prepare(readFileSync(new URL('../scripts/audit-polls.sql',import.meta.url),'utf8')).all();
    expect(db.prepare('SELECT total_changes() AS n').get()).toEqual(before);
    expect(auditRows(rows)[0]).toMatchObject({ survivingStudents:1, assignedWithoutVote:1, minimumNegative:0 });
  } finally {db.close();}
});
