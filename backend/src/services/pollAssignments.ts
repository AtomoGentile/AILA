import type { Env } from '../types';
import { newUUID } from '../auth';
import { maximizeAssignment } from '../algorithms/interrogationAssignment';

export const ALGORITHM_VERSION = 'global-score-v1';
export const POLL_RULES = { fairNegativeShare: 0.4, maxSacrificeBonus: 300, lightRedBonus: 40, darkRedBonus: 150 };
export interface CalculationInput {
  subject: string;
  closesAt: string | null;
  slots: Array<{ id: string; date: string; capacity: number }>;
  students: Record<string, { bonus: number; submitted: number; votes: Record<string, number> }>;
}
export interface Assignment { studentId: string; slotId: string; voteScore: number }
export class PollCalculationError extends Error {}

// Lettura coerente e ordinata degli input. Lo stesso JSON è confrontato nella transazione finale.
const INPUT_SQL = `SELECT json_object(
  'subject', g.subject, 'closesAt', g.closes_at,
  'slots', json((SELECT json_group_array(json_object('id', s.id, 'date', s.slot_date, 'capacity', s.capacity))
    FROM (SELECT id, slot_date, capacity FROM interrogation_slots WHERE grid_id = g.id ORDER BY slot_date, id) s)),
  'students', json((SELECT json_group_object(u.id, json_object(
    'submitted', EXISTS(SELECT 1 FROM interrogation_submissions WHERE grid_id = g.id AND student_id = u.id),
    'bonus', COALESCE((SELECT bonus_points FROM student_sacrifice_bonus WHERE student_id = u.id AND subject = g.subject), 0),
    'votes', json((SELECT json_group_object(v.slot_id, v.vote_score)
      FROM (SELECT iv.slot_id, iv.vote_score FROM interrogation_votes iv JOIN interrogation_slots s ON s.id = iv.slot_id
        WHERE s.grid_id = g.id AND iv.student_id = u.id ORDER BY iv.slot_id) v))
  )) FROM (SELECT id FROM users WHERE class_id = g.class_id AND role IN ('STUDENT', 'REPRESENTATIVE')
    AND (g.audience_json IS NULL OR id IN (SELECT value FROM json_each(g.audience_json))) ORDER BY id) u))
) AS snapshot FROM interrogation_grids g WHERE g.id = ? AND g.class_id = ? AND g.is_published = 1`;

export async function isGridCalculated(env: Env, gridId: string): Promise<boolean> {
  return !!await env.DB.prepare(`SELECT 1 AS done WHERE
    EXISTS(SELECT 1 FROM interrogation_calculations WHERE grid_id = ?)
    OR EXISTS(SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = ?)`)
    .bind(gridId, gridId).first();
}

export function planAssignments(input: CalculationInput, preferredSlots?: ReadonlyMap<string, string>) {
  const students = Object.keys(input.students);
  if (!students.length) throw new PollCalculationError('Non ci sono destinatari da assegnare');
  if (input.slots.some(s => !Number.isSafeInteger(s.capacity) || s.capacity < 1) ||
    input.slots.reduce((sum, s) => sum + s.capacity, 0) < students.length) {
    throw new PollCalculationError('La capienza delle date non basta per tutti i destinatari');
  }
  const weights = Object.fromEntries(students.map(id => {
    const negatives = Object.values(input.students[id].votes).filter(v => v < 0).length;
    const fair = Math.max(1, Math.floor(input.slots.length * POLL_RULES.fairNegativeShare));
    return [id, negatives === 0 ? 1 : Math.min(1, fair / negatives)];
  }));
  const score = (student: string, slot: { id: string }) => {
    const profile = input.students[student], vote = profile.votes[slot.id] ?? 0;
    return (vote < 0 ? vote * weights[student] : vote) + (vote === 50 ? Math.min(profile.bonus, POLL_RULES.maxSacrificeBonus) : 0);
  };
  const solution = maximizeAssignment(students, input.slots, score, preferredSlots);
  if (solution.flow !== students.length) throw new PollCalculationError('Impossibile assegnare tutti i destinatari');
  const assignments = solution.assignments.map(a => ({ ...a, voteScore: score(a.studentId, { id: a.slotId }) }));
  const results = Object.fromEntries(assignments.map(a => {
    const profile = input.students[a.studentId], vote = profile.votes[a.slotId] ?? 0;
    const gain = vote < 0 ? Math.round((vote === -300 ? POLL_RULES.darkRedBonus : POLL_RULES.lightRedBonus) * weights[a.studentId]) : 0;
    return [a.studentId, {
      slotId: a.slotId, vote: profile.votes[a.slotId] ?? null, effectiveScore: a.voteScore,
      negativeWeight: weights[a.studentId], bonusBefore: profile.bonus,
      bonusAfter: vote < 0 ? Math.min(profile.bonus + gain, POLL_RULES.maxSacrificeBonus) : 0,
    }];
  }));
  return { assignments, results };
}

/** NULL significa che un'altra richiesta ha già chiuso il sondaggio. */
export async function computeAndPersistAssignments(env: Env, gridId: string, classId: string, force = false): Promise<Assignment[] | null> {
  for (let attempt = 0; attempt < 3; attempt++) {
    if (await isGridCalculated(env, gridId)) return null;
    const row = await env.DB.prepare(INPUT_SQL).bind(gridId, classId).first<{ snapshot: string }>();
    if (!row) throw new PollCalculationError('Sondaggio non disponibile per il calcolo');
    const input: CalculationInput = JSON.parse(row.snapshot), students = Object.keys(input.students);
    if (!students.length) throw new PollCalculationError('Non ci sono destinatari da assegnare');
    const expired = input.closesAt !== null && new Date(input.closesAt).getTime() <= Date.now();
    if (!force && !expired && students.some(id => !input.students[id].submitted)) {
      throw new PollCalculationError('Mancano ancora invii: attendi tutti gli studenti o forza la chiusura.');
    }
    const { assignments, results } = planAssignments(input);
    const runId = newUUID();
    const ownRun = 'EXISTS(SELECT 1 FROM interrogation_calculations WHERE grid_id = ? AND run_id = ?)';
    const batch = await env.DB.batch([
      // Claim, risultati e bonus sono nella stessa transazione: nessun lock abbandonato,
      // nessun doppio accredito. Un input cambiato durante il calcolo impone un retry.
      env.DB.prepare(`INSERT OR IGNORE INTO interrogation_calculations
        (grid_id, run_id, algorithm_version, input_json, result_json, rules_json)
        SELECT ?, ?, ?, ?, ?, ? WHERE ? = (${INPUT_SQL})
        AND NOT EXISTS(SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = ?)`)
        .bind(gridId, runId, ALGORITHM_VERSION, row.snapshot, JSON.stringify(results), JSON.stringify(POLL_RULES), row.snapshot, gridId, classId, gridId),
      ...assignments.map(a => env.DB.prepare(`INSERT INTO interrogation_assignments (id, slot_id, student_id)
        SELECT ?, ?, ? WHERE ${ownRun}`).bind(newUUID(), a.slotId, a.studentId, gridId, runId)),
      ...assignments.map(a => {
        const result = results[a.studentId];
        return result.bonusAfter > 0
          ? env.DB.prepare(`INSERT INTO student_sacrifice_bonus (student_id, subject, bonus_points)
              SELECT ?, ?, ? WHERE ${ownRun}
              ON CONFLICT(student_id, subject) DO UPDATE SET bonus_points = excluded.bonus_points`)
              .bind(a.studentId, input.subject, result.bonusAfter, gridId, runId)
          : env.DB.prepare(`DELETE FROM student_sacrifice_bonus WHERE student_id = ? AND subject = ? AND ${ownRun}`)
              .bind(a.studentId, input.subject, gridId, runId);
      }),
    ]);
    if (batch[0].meta.changes) return assignments;
    if (await isGridCalculated(env, gridId)) return null;
  }
  throw new PollCalculationError('I dati del sondaggio sono cambiati durante il calcolo. Riprova.');
}
