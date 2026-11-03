import type { Env } from '../types';
import { newUUID } from '../auth';
import { fullName } from '../names';
import { PREVIEW_SQL, stateToken, type PreviewData } from './pollPreview';
import { PollCalculationError } from './pollAssignments';
import { ownedEvents, insertEvents, deleteEvents } from './pollRevisions';

const STALE = 'Le assegnazioni o il calendario sono cambiati. Riapri la sincronizzazione prima di confermare.';

function plannedEvents(data: PreviewData, gridId: string, classId: string, actorId: string) {
  const groups = new Map<string, string[]>();
  for (const a of data.current) {
    const p = data.students[a.studentId];
    if (!p) throw new PollCalculationError('I partecipanti sono cambiati. Aggiorna i risultati.');
    groups.set(a.slotId, [...(groups.get(a.slotId) ?? []), fullName(p.firstName, p.lastName)]);
  }
  return [...groups].map(([slotId, names]) => {
    const existing = data.calendar.find(e => e.id === `poll:${gridId}:${slotId}` && e.poll_grid_id === gridId);
    return { id: `poll:${gridId}:${slotId}`, title: `Interrogazione di ${data.subject}`,
      event_date: data.slots.find(s => s.id === slotId)!.date, start_time: existing?.start_time ?? null,
      category: 'INTERROGAZIONE', is_for_all: 1, is_ai_generated: 1, created_by: existing?.created_by ?? actorId,
      class_id: classId, created_at: existing?.created_at ?? new Date().toISOString(),
      notes: `Studenti: ${names.sort((a, b) => a.localeCompare(b, 'it')).join(', ')}`,
      poll_grid_id: gridId, visible_to_user_ids_json: null, circular_number: null };
  }).sort((a, b) => a.event_date.localeCompare(b.event_date));
}

function candidates(data: PreviewData, gridId: string) {
  return data.calendar.filter(e => e.poll_grid_id == null && e.is_for_all === 1 && e.visible_to_user_ids_json == null &&
    e.category === 'INTERROGAZIONE' && e.title === `Interrogazione di ${data.subject}` && e.circular_number == null);
}

export async function previewCalendarSync(env: Env, gridId: string, classId: string, actorId: string) {
  const row = await env.DB.prepare(PREVIEW_SQL).bind(gridId, classId).first<{ data: string }>();
  if (!row) return null;
  const data: PreviewData = JSON.parse(row.data);
  if (!data.current.length) throw new PollCalculationError('Non ci sono risultati pubblicati da sincronizzare.');
  const recognized = new Set(ownedEvents(data, gridId).map(e => e.id));
  return { subject: data.subject, syncToken: await stateToken(row.data),
    dates: plannedEvents(data, gridId, classId, actorId).map(e => ({ date: e.event_date, notes: e.notes })),
    linkedCount: data.calendar.filter(e => e.poll_grid_id === gridId).length,
    previousEvents: candidates(data, gridId).sort((a, b) => a.event_date.localeCompare(b.event_date)).map(e => ({
      id: e.id, date: e.event_date, notes: e.notes, startTime: e.start_time,
      selectedByDefault: recognized.has(e.id) && !data.otherAssignedDates.includes(e.event_date),
    })) };
}

export async function syncPollCalendar(env: Env, gridId: string, classId: string, actorId: string, token: string, replaceIds: string[]) {
  const row = await env.DB.prepare(PREVIEW_SQL).bind(gridId, classId).first<{ data: string }>();
  if (!row || await stateToken(row.data) !== token) throw new PollCalculationError(STALE);
  const data: PreviewData = JSON.parse(row.data);
  if (!data.current.length) throw new PollCalculationError('Non ci sono risultati pubblicati da sincronizzare.');
  const allowed = candidates(data, gridId), selected = new Set(replaceIds);
  if (selected.size !== replaceIds.length || replaceIds.some(id => !allowed.some(e => e.id === id))) throw new PollCalculationError('Una delle vecchie date selezionate non è sostituibile per questo sondaggio.');
  const events = plannedEvents(data, gridId, classId, actorId), runId = newUUID();
  // L'undo di una revisione rimane disponibile solo se era già sicuro prima della sincronizzazione.
  const revision = data.revision ? await env.DB.prepare('SELECT before_state, after_state FROM interrogation_revisions WHERE id=?')
    .bind(data.revision.id).first<{ before_state: string | null; after_state: string | null }>() : null;
  const keepRestore = !!revision?.before_state && revision.after_state === row.data;
  let beforeRevision = revision?.before_state ?? null;
  if (keepRestore) {
    const before = JSON.parse(beforeRevision!);
    before.calendarRestoreEventIds = [...new Set([...(before.calendarRestoreEventIds ?? []), ...replaceIds])];
    beforeRevision = JSON.stringify(before);
  }
  const guard = 'EXISTS(SELECT 1 FROM interrogation_calendar_syncs WHERE id=?)';
  const batch = [env.DB.prepare(`INSERT INTO interrogation_calendar_syncs(id, grid_id)
    SELECT ?, ? WHERE ? = (${PREVIEW_SQL})`).bind(runId, gridId, row.data, gridId, classId)];
  batch.push(deleteEvents(env, data.calendar.filter(e => e.poll_grid_id === gridId || selected.has(e.id)), guard, runId));
  batch.push(insertEvents(env, events, guard, runId));
  if (keepRestore) batch.push(env.DB.prepare(`UPDATE interrogation_revisions SET before_state=?, after_state=(${PREVIEW_SQL})
    WHERE id=? AND before_state=? AND after_state=? AND ${guard}`)
    .bind(beforeRevision, gridId, classId, data.revision!.id, revision!.before_state, revision!.after_state, runId));
  const result = await env.DB.batch(batch);
  if (!result[0].meta.changes) throw new PollCalculationError(STALE);
  return { success: true, dateCount: events.length, replacedCount: selected.size };
}
