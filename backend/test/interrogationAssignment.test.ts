import { expect, it } from 'vitest';
import { maximizeAssignment } from '../src/algorithms/interrogationAssignment';

it('evita il rosso e il giallo evitabili, anche quando uno studente ha bonus', () => {
  for (const secondVote of [0, -60, -300]) {
    const matrix: Record<string, number[]> = { flexible: [90, 90], constrained: [50, secondVote] };
    const result = maximizeAssignment(['flexible', 'constrained'], [{ id: '0', capacity: 1 }, { id: '1', capacity: 1 }],
      (student, slot) => matrix[student][Number(slot.id)]);
    expect(result.flow).toBe(2);
    expect(result.assignments).toEqual([{ studentId: 'flexible', slotId: '1' }, { studentId: 'constrained', slotId: '0' }]);
    expect(result.score).toBe(140);
  }
});

it('massimizza il punteggio su tutte le 4096 combinazioni 2x2 di voti e bonus', () => {
  const slots = [{ id: '0', capacity: 1 }, { id: '1', capacity: 1 }];
  for (const a of [50, 0, -60, -300]) for (const b of [50, 0, -60, -300])
    for (const c of [50, 0, -60, -300]) for (const d of [50, 0, -60, -300])
      for (const bonusA of [0, 40, 150, 300]) for (const bonusB of [0, 40, 150, 300]) {
        const weighted = (votes: number[], bonus: number) => {
          const weight = Math.min(1, 1 / Math.max(1, votes.filter(v => v < 0).length));
          return votes.map(v => v === 50 ? v + bonus : v < 0 ? v * weight : 0);
        };
        const matrix = [weighted([a, b], bonusA), weighted([c, d], bonusB)];
        const result = maximizeAssignment(['0', '1'], slots, (student, slot) => matrix[Number(student)][Number(slot.id)]);
        expect(result.score).toBeCloseTo(Math.max(matrix[0][0] + matrix[1][1], matrix[0][1] + matrix[1][0]), 8);
        expect(new Set(result.assignments.map(a => a.slotId)).size).toBe(2);
      }
});

it('rispetta capienze multiple e trova anche miglioramenti che richiedono catene di spostamenti', () => {
  const slots = [{ id: '0', capacity: 1 }, { id: '1', capacity: 2 }, { id: '2', capacity: 1 }];
  let seed = 12345;
  const next = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 2 ** 32; };
  for (let iteration = 0; iteration < 100; iteration++) {
    const matrix = Array.from({ length: 4 }, () => Array.from({ length: 3 }, () => (Math.floor(next() * 651) - 300) / 3));
    const result = maximizeAssignment(['0', '1', '2', '3'], slots, (student, slot) => matrix[Number(student)][Number(slot.id)]);
    let best = -Infinity;
    for (let a = 0; a < 3; a++) for (let b = 0; b < 3; b++) for (let c = 0; c < 3; c++) for (let d = 0; d < 3; d++) {
      const selection = [a, b, c, d];
      if (slots.some((s, i) => selection.filter(x => x === i).length > s.capacity)) continue;
      best = Math.max(best, selection.reduce((sum, slot, student) => sum + matrix[student][slot], 0));
    }
    expect(result.flow).toBe(4);
    expect(result.score).toBeCloseTo(best, 8);
    for (const slot of slots) expect(result.assignments.filter(a => a.slotId === slot.id).length).toBeLessThanOrEqual(slot.capacity);
  }
});

it('in anteprima conserva le date attuali a parità di punteggio, senza rinunciare a miglioramenti reali', () => {
  const slots = [{ id: '0', capacity: 1 }, { id: '1', capacity: 1 }];
  const preferred = new Map([['a', '1'], ['b', '0']]);
  const tied = maximizeAssignment(['a', 'b'], slots, () => 50, preferred);
  expect(tied.assignments).toEqual([{ studentId: 'a', slotId: '1' }, { studentId: 'b', slotId: '0' }]);
  const better = maximizeAssignment(['a', 'b'], slots, (student, slot) => (student === 'a') === (slot.id === '0') ? 51 : 50, preferred);
  expect(better.score).toBe(102);
  expect(better.assignments).toEqual([{ studentId: 'a', slotId: '0' }, { studentId: 'b', slotId: '1' }]);
});

it('minimizza gli spostamenti anche con capienze multiple e molti pareggi', () => {
  const students = ['0', '1', '2', '3'];
  const slots = [{ id: '0', capacity: 1 }, { id: '1', capacity: 2 }, { id: '2', capacity: 1 }];
  const baseline = [0, 1, 1, 2];
  const preferred = new Map(students.map((student, i) => [student, String(baseline[i])]));
  let seed = 42;
  for (let iteration = 0; iteration < 100; iteration++) {
    const matrix = students.map(() => slots.map(() => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed % 3 * 50; }));
    let bestScore = -Infinity, fewestMoves = Infinity;
    for (let a = 0; a < 3; a++) for (let b = 0; b < 3; b++) for (let c = 0; c < 3; c++) for (let d = 0; d < 3; d++) {
      const selection = [a, b, c, d];
      if (slots.some((slot, i) => selection.filter(s => s === i).length > slot.capacity)) continue;
      const score = selection.reduce((sum, slot, student) => sum + matrix[student][slot], 0);
      const moves = selection.filter((slot, student) => slot !== baseline[student]).length;
      if (score > bestScore || (score === bestScore && moves < fewestMoves)) { bestScore = score; fewestMoves = moves; }
    }
    const result = maximizeAssignment(students, slots, (student, slot) => matrix[Number(student)][Number(slot.id)], preferred);
    expect(result.score).toBe(bestScore);
    expect(result.assignments.filter(a => preferred.get(a.studentId) !== a.slotId).length).toBe(fewestMoves);
  }
});
