import type { Env } from '../types';
import { newUUID } from '../auth';
import { fullName } from '../names';
import { planAssignments, PollCalculationError } from './pollAssignments';
import { PREVIEW_SQL, previewAssignments, stateToken, type PreviewBasis, type PreviewData } from './pollPreview';

const STALE = 'Il calendario, i bonus o gli altri dati sono cambiati. Aggiorna il confronto prima di confermare.';
const EVENT_COLUMNS = ['id', 'title', 'event_date', 'start_time', 'category', 'is_for_all', 'is_ai_generated', 'created_by', 'class_id', 'created_at', 'notes', 'visible_to_user_ids_json', 'circular_number', 'poll_grid_id'];

// Le vecchie date senza collegamento vengono riconosciute solo dal contenuto completo.
// Un evento manuale, di una circolare o con note diverse non viene sovrascritto.
function ownedEvents(data: PreviewData, gridId: string) {
  const groups = new Map<string, string[]>();
  for (const a of data.current) {
    const date = data.slots.find(s => s.id === a.slotId)!.date;
    const p = data.students[a.studentId];
    groups.set(date, [...(groups.get(date) ?? []), fullName(p.firstName, p.lastName)]);
  }
  return data.calendar.filter(e => e.poll_grid_id === gridId || (
    e.poll_grid_id == null && e.is_ai_generated === 1 && e.is_for_all === 1 && e.circular_number == null &&
    e.start_time == null && e.visible_to_user_ids_json == null &&
    e.title === `Interrogazione di ${data.subject}` && typeof e.notes === 'string' && e.notes.startsWith('Studenti: ') &&
    JSON.stringify(e.notes.slice(10).split(', ').sort()) === JSON.stringify([...(groups.get(e.event_date) ?? [])].sort())
  ));
}

function saveBonuses(env: Env, bonuses: Record<string, number>, subject: string, guard: string, guardId: string) {
  return env.DB.prepare(`INSERT INTO student_sacrifice_bonus (student_id, subject, bonus_points)
    SELECT j.key, ?, j.value FROM json_each(?) j WHERE ${guard}
    ON CONFLICT(student_id, subject) DO UPDATE SET bonus_points = excluded.bonus_points`)
    .bind(subject, JSON.stringify(bonuses), guardId);
}

function saveAssignments(env: Env, assignments: Record<string, string>, gridId: string, guard: string, guardId: string) {
  return env.DB.prepare(`UPDATE interrogation_assignments SET slot_id =
    (SELECT j.value FROM json_each(?) j WHERE j.key = interrogation_assignments.student_id)
    WHERE slot_id IN (SELECT id FROM interrogation_slots WHERE grid_id = ?) AND ${guard}`)
    .bind(JSON.stringify(assignments), gridId, guardId);
}

function insertEvents(env: Env, events: Record<string, any>[], guard: string, guardId: string) {
  return env.DB.prepare(`INSERT INTO calendar_events (${EVENT_COLUMNS.join(',')})
    SELECT ${EVENT_COLUMNS.map(c => `json_extract(j.value, '$.${c}')`).join(',')}
    FROM json_each(?) j WHERE ${guard}`).bind(JSON.stringify(events), guardId);
}

function deleteEvents(env: Env, events: Record<string, any>[], guard: string, guardId: string) {
  return env.DB.prepare(`DELETE FROM calendar_events WHERE id IN (SELECT value FROM json_each(?)) AND ${guard}`)
    .bind(JSON.stringify(events.map(e => e.id)), guardId);
}

export async function publishRevision(env: Env, gridId: string, classId: string, actorId: string, basis: PreviewBasis, token: string) {
  const preview = await previewAssignments(env, gridId, classId, basis);
  if (!preview) throw new PollCalculationError('Sondaggio non trovato');
  if (!preview.canPublish) throw new PollCalculationError('Esiste già una revisione pubblicata. Ripristina prima il calendario precedente.');
  if (preview.revisionToken !== token) throw new PollCalculationError(STALE);
  if (!preview.moved) throw new PollCalculationError('Non ci sono date da modificare con questo confronto.');
  const row = await env.DB.prepare(PREVIEW_SQL).bind(gridId, classId).first<{ data: string }>();
  if (!row || await stateToken(row.data) !== token) throw new PollCalculationError(STALE);
  const data: PreviewData = JSON.parse(row.data), id = newUUID();
  const legacyEvents = ownedEvents(data, gridId).filter(e => e.poll_grid_id == null);
  if (legacyEvents.some(e => data.otherAssignedDates.includes(e.event_date))) throw new PollCalculationError(
    'Una vecchia data del calendario può appartenere anche a un altro sondaggio della stessa materia. Rimuovi o distingui quell’evento prima di pubblicare la revisione.');
  const guard = 'EXISTS(SELECT 1 FROM interrogation_revisions WHERE id = ? AND restored_at IS NULL)';
  const statements = [env.DB.prepare(`INSERT OR IGNORE INTO interrogation_revisions
    (id, grid_id, basis, bonus_policy, before_state, created_by)
    SELECT ?, ?, ?, ?, ?, ? WHERE ? = (${PREVIEW_SQL})
    AND NOT EXISTS(SELECT 1 FROM interrogation_revisions WHERE grid_id = ? AND restored_at IS NULL)`)
    .bind(id, gridId, basis, preview.bonusPolicy, row.data, actorId, row.data, gridId, classId, gridId)];
  statements.push(saveAssignments(env, Object.fromEntries(preview.assignments.map(a => [a.studentId, a.afterSlotId])), gridId, guard, id));
  if (preview.bonusPolicy === 'RECONCILE') {
    const results = planAssignments(data.original!, new Map(data.current.map(a => [a.studentId, a.slotId]))).results;
    statements.push(saveBonuses(env, Object.fromEntries(Object.entries(results).map(([studentId, r]) => [studentId, r.bonusAfter])), data.subject, guard, id));
  }
  statements.push(deleteEvents(env, ownedEvents(data, gridId), guard, id));
  const groups = new Map<string, string[]>();
  for (const a of preview.assignments) groups.set(a.afterSlotId, [...(groups.get(a.afterSlotId) ?? []), a.studentName]);
  const events = [...groups].map(([slotId, names]) => ({
    id: `poll:${gridId}:${slotId}`, title: `Interrogazione di ${data.subject}`,
    event_date: data.slots.find(s => s.id === slotId)!.date, start_time: null, category: 'INTERROGAZIONE',
    is_for_all: 1, is_ai_generated: 1, created_by: actorId, class_id: classId, created_at: new Date().toISOString(),
    notes: `Studenti: ${names.sort().join(', ')}`, poll_grid_id: gridId, visible_to_user_ids_json: null, circular_number: null,
  }));
  statements.push(insertEvents(env, events, guard, id));
  statements.push(env.DB.prepare(`UPDATE swap_requests SET status = 'REJECTED' WHERE grid_id = ? AND status = 'PENDING' AND ${guard}`).bind(gridId, id));
  // Fotografia post-pubblicazione nella stessa transazione: serve per un ripristino sicuro.
  statements.push(env.DB.prepare(`UPDATE interrogation_revisions SET after_state = (${PREVIEW_SQL}) WHERE id = ? AND ${guard}`).bind(gridId, classId, id, id));
  const result = await env.DB.batch(statements);
  if (!result[0].meta.changes) throw new PollCalculationError(STALE);
  return { success: true, revisionId: id, bonusPolicy: preview.bonusPolicy, moved: preview.moved };
}

export async function restoreRevision(env: Env, gridId: string, classId: string, revisionId: string, token: string) {
  const revision = await env.DB.prepare(`SELECT r.before_state, r.after_state, r.bonus_policy FROM interrogation_revisions r
    JOIN interrogation_grids g ON g.id = r.grid_id WHERE r.id = ? AND r.grid_id = ? AND g.class_id = ? AND r.restored_at IS NULL`)
    .bind(revisionId, gridId, classId).first<{ before_state: string | null; after_state: string | null; bonus_policy: string }>();
  if (!revision?.before_state || !revision.after_state) throw new PollCalculationError('La revisione non è ripristinabile: lo storico completo non è più disponibile.');
  const current = await env.DB.prepare(PREVIEW_SQL).bind(gridId, classId).first<{ data: string }>();
  if (!current || current.data !== revision.after_state || await stateToken(current.data) !== token) throw new PollCalculationError(
    'Dopo la pubblicazione sono cambiati dati, bonus, scambi o calendario. Il ripristino automatico non può sovrascriverli.');
  const before: PreviewData = JSON.parse(revision.before_state), after: PreviewData = JSON.parse(revision.after_state);
  const runId = newUUID(), guard = 'EXISTS(SELECT 1 FROM interrogation_revisions WHERE restore_run_id = ?)';
  const statements = [env.DB.prepare(`UPDATE interrogation_revisions SET restore_run_id = ? WHERE id = ? AND restored_at IS NULL
    AND restore_run_id IS NULL AND after_state = (${PREVIEW_SQL})`).bind(runId, revisionId, gridId, classId)];
  statements.push(saveAssignments(env, Object.fromEntries(before.current.map(a => [a.studentId, a.slotId])), gridId, guard, runId));
  if (revision.bonus_policy === 'RECONCILE') statements.push(saveBonuses(env, Object.fromEntries(Object.entries(before.students).map(([id, p]) => [id, p.bonus])), before.subject, guard, runId));
  statements.push(deleteEvents(env, after.calendar.filter(e => e.poll_grid_id === gridId), guard, runId));
  statements.push(insertEvents(env, ownedEvents(before, gridId), guard, runId));
  statements.push(env.DB.prepare(`UPDATE interrogation_revisions SET restored_at = CURRENT_TIMESTAMP WHERE id = ? AND ${guard}`).bind(revisionId, runId));
  const result = await env.DB.batch(statements);
  if (!result[0].meta.changes) throw new PollCalculationError(STALE);
  return { success: true };
}
