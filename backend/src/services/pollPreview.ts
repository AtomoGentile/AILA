import type { Env } from '../types';
import { fullName } from '../names';
import { ALGORITHM_VERSION, planAssignments, PollCalculationError, type CalculationInput } from './pollAssignments';

export type PreviewBasis = 'ORIGINAL' | 'VOTES_ONLY' | 'CURRENT_BONUSES';
export type PreviewRequestBasis = PreviewBasis | 'AUTO';
interface PreviewData {
  subject: string;
  slots: CalculationInput['slots'];
  students: Record<string, { firstName: string; lastName: string; bonus: number; votes: Record<string, number> }>;
  current: Array<{ studentId: string; slotId: string }>;
  original: CalculationInput | null;
  redacted: number;
  acceptedSwaps: number;
}

// Una sola lettura coerente. La simulazione non usa la funzione che salva risultati e bonus.
const PREVIEW_SQL = `SELECT json_object(
  'subject', g.subject,
  'slots', json((SELECT json_group_array(json_object('id', s.id, 'date', s.slot_date, 'capacity', s.capacity))
    FROM (SELECT id, slot_date, capacity FROM interrogation_slots WHERE grid_id = g.id ORDER BY slot_date, id) s)),
  'students', json((SELECT json_group_object(u.id, json_object(
    'firstName', u.first_name, 'lastName', u.last_name,
    'bonus', COALESCE((SELECT bonus_points FROM student_sacrifice_bonus WHERE student_id = u.id AND subject = g.subject), 0),
    'votes', json((SELECT json_group_object(v.slot_id, v.vote_score) FROM
      (SELECT iv.slot_id, iv.vote_score FROM interrogation_votes iv JOIN interrogation_slots s ON s.id = iv.slot_id
       WHERE s.grid_id = g.id AND iv.student_id = u.id ORDER BY iv.slot_id) v))
  )) FROM (SELECT id, first_name, last_name FROM users WHERE class_id = g.class_id AND id IN
    (SELECT a.student_id FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = g.id) ORDER BY id) u)),
  'current', json((SELECT json_group_array(json_object('studentId', a.student_id, 'slotId', a.slot_id)) FROM
    (SELECT a.student_id, a.slot_id FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = g.id ORDER BY a.student_id) a)),
  'original', json((SELECT input_json FROM interrogation_calculations WHERE grid_id = g.id)),
  'redacted', COALESCE((SELECT redacted_students FROM interrogation_calculations WHERE grid_id = g.id), 0),
  'acceptedSwaps', (SELECT COUNT(*) FROM swap_requests WHERE grid_id = g.id AND status = 'ACCEPTED')
) AS data FROM interrogation_grids g WHERE g.id = ? AND g.class_id = ?`;

export async function previewAssignments(env: Env, gridId: string, classId: string, requested?: PreviewRequestBasis) {
  const row = await env.DB.prepare(PREVIEW_SQL).bind(gridId, classId).first<{ data: string }>();
  if (!row) return null;
  const data: PreviewData = JSON.parse(row.data);
  const studentIds = Object.keys(data.students), current = new Map(data.current.map(a => [a.studentId, a.slotId]));
  if (!data.current.length) throw new PollCalculationError('Non ci sono assegnazioni da confrontare per questo sondaggio.');
  if (current.size !== data.current.length || studentIds.length !== current.size || studentIds.some(id => !current.has(id))) {
    throw new PollCalculationError('I partecipanti del calendario sono cambiati: impossibile confrontare tutte le assegnazioni.');
  }
  const originalAvailable = !!data.original && data.redacted === 0 &&
    Object.keys(data.original.students).length === studentIds.length && studentIds.every(id => id in data.original!.students) &&
    data.original.slots.length === data.slots.length && data.slots.every(slot => data.original!.slots.some(old => old.id === slot.id && old.capacity === slot.capacity && old.date === slot.date));
  const basis: PreviewBasis = requested === 'AUTO'
    ? (originalAvailable ? 'ORIGINAL' : 'CURRENT_BONUSES')
    : requested ?? (originalAvailable ? 'ORIGINAL' : 'VOTES_ONLY');
  if (basis === 'ORIGINAL' && !originalAvailable) throw new PollCalculationError('I voti e i bonus iniziali completi non sono disponibili per questo sondaggio.');
  const generatedAt = new Date().toISOString();
  const build = (basis: PreviewBasis) => {
    const input: CalculationInput = basis === 'ORIGINAL' ? data.original! : {
      subject: data.subject, closesAt: null, slots: data.slots,
      students: Object.fromEntries(studentIds.map(id => [id, { bonus: basis === 'CURRENT_BONUSES' ? data.students[id].bonus : 0, submitted: 1, votes: data.students[id].votes }])),
    };
    // A parità di punteggio ottimo conserva più date attuali possibili, inclusi gli scambi.
    const plan = planAssignments(input, current);
    const slotDates = new Map(data.slots.map(s => [s.id, s.date]));
    const rows = plan.assignments.map(a => {
      const oldSlot = current.get(a.studentId)!;
      const beforeVote = input.students[a.studentId].votes[oldSlot] ?? null;
      const afterVote = input.students[a.studentId].votes[a.slotId] ?? null;
      const delta = (afterVote ?? 0) - (beforeVote ?? 0);
      return {
        studentId: a.studentId,
        bonusUsed: input.students[a.studentId].bonus,
        studentName: fullName(data.students[a.studentId].firstName, data.students[a.studentId].lastName),
        beforeSlotId: oldSlot, beforeDate: slotDates.get(oldSlot)!, beforeVote,
        afterSlotId: a.slotId, afterDate: slotDates.get(a.slotId)!, afterVote,
        changed: oldSlot !== a.slotId,
        preferenceChange: delta > 0 ? 'IMPROVED' : delta < 0 ? 'WORSENED' : 'SAME',
      };
    }).sort((a, b) => Number(b.changed) - Number(a.changed) || a.studentName.localeCompare(b.studentName, 'it'));
    const distribution = (key: 'beforeVote' | 'afterVote') => ({
      green: rows.filter(r => r[key] === 50).length,
      yellow: rows.filter(r => r[key] === 0).length,
      redLight: rows.filter(r => r[key] === -60).length,
      redDark: rows.filter(r => r[key] === -300).length,
      missing: rows.filter(r => r[key] === null).length,
    });
    return {
      subject: data.subject, algorithmVersion: ALGORITHM_VERSION, basis, originalAvailable,
      generatedAt, acceptedSwaps: data.acceptedSwaps,
      historicalDataIncomplete: data.redacted > 0,
      totalStudents: rows.length, moved: rows.filter(r => r.changed).length,
      improved: rows.filter(r => r.preferenceChange === 'IMPROVED').length,
      worsened: rows.filter(r => r.preferenceChange === 'WORSENED').length,
      before: distribution('beforeVote'), after: distribution('afterVote'), assignments: rows,
    };
  };
  const selected = build(basis);
  if (requested !== 'AUTO') return { ...selected, comparisons: [] };
  // Tutti i confronti di una generazione condividono gli stessi bonus e dati letti una volta.
  const modes: PreviewBasis[] = originalAvailable ? ['ORIGINAL', 'CURRENT_BONUSES', 'VOTES_ONLY'] : ['CURRENT_BONUSES', 'VOTES_ONLY'];
  return { ...selected, comparisons: modes.filter(mode => mode !== basis).map(build) };
}
