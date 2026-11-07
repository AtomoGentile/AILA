import { studyAudience } from '../services/studyAudience';
// =============================================================================
// CIRCOLARE+ — Calendar Routes (/api/calendar/*)
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, EventCategory } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';

/** HH:MM oppure un'''ora di lezione 1-6: "3ª ora" o "Dalla 2ª alla 4ª ora". */
function isValidEventTime(value: string): boolean {
  if (/^([01]?\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(value)) return true;
  const single = /^([1-6])ª ora$/.exec(value);
  if (single) return true;
  const range = /^Dalla ([1-6])ª alla ([1-6])ª ora$/.exec(value);
  return range !== null && Number(range[1]) <= Number(range[2]);
}

function isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}


const calendar = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

calendar.use('*', authMiddleware());

const VALID_CATEGORIES: EventCategory[] = [
  'VERIFICA', 'INTERROGAZIONE', 'PAGAMENTO', 'USCITA_DIDATTICA', 'ENTRATA', 'AVVISO', 'ALTRO',
];

const MAX_TITLE_LENGTH = 200;
const MAX_NOTES_LENGTH = 2000;

/** "Verifica di Matematica " e "verifica di matematica" sono lo stesso titolo. */
export function normalizeTitle(title: string): string {
  return title
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

// ---------------------------------------------------------------------------
// GET /api/calendar — Lista eventi (con filtri)
// ---------------------------------------------------------------------------
calendar.get('/', async (c) => {
  const category = c.req.query('category') as EventCategory | undefined;
  const from = c.req.query('from'); // yyyy-mm-dd
  const to = c.req.query('to');     // yyyy-mm-dd

  // Filtro di classe: senza, il calendario mostrava gli eventi di tutte le classi mescolati.
  const classId = await resolveClassId(c);

  // Gli eventi per "Persone specifiche" li vedono solo chi li ha creati e i destinatari: prima
  // arrivavano a tutta la classe (li filtrava solo l'assistente).
  const me = c.get('jwtPayload').sub;
  let query = `SELECT *, ${studyAudience("calendar_events")} AS is_relevant_to_me FROM calendar_events WHERE class_id = ?
    AND (is_for_all = 1 OR visible_to_user_ids_json IS NULL OR created_by = ?
         OR EXISTS (SELECT 1 FROM json_each(calendar_events.visible_to_user_ids_json) WHERE value = ?))`;
  const params: (string | number)[] = [me,me,me,classId, me, me];

  if (category && VALID_CATEGORIES.includes(category)) {
    query += ' AND category = ?';
    params.push(category);
  }
  if (from) {
    query += ' AND event_date >= ?';
    params.push(from);
  }
  if (to) {
    query += ' AND event_date <= ?';
    params.push(to);
  }

  // Le ore di lezione ("3ª ora", "Dalla 2ª alla 4ª ora") si ordinano per ora d'inizio, prima degli orari.
  query += " ORDER BY event_date ASC, CASE WHEN start_time LIKE 'Dalla %' THEN '0' || substr(start_time, 7, 1) WHEN start_time LIKE '_ª ora' THEN '0' || substr(start_time, 1, 1) ELSE start_time END ASC";

  const rows = await c.env.DB.prepare(query).bind(...params).all<{
    id: string;
    title: string;
    event_date: string;
    start_time: string | null;
    category: string;
    is_for_all: number;
    is_relevant_to_me:number;
    is_ai_generated: number;
    created_by: string | null;
    created_at: string;
    notes: string | null;
    visible_to_user_ids_json: string | null;
    circular_number?: number | null;
  }>();

  return c.json({
    events: rows.results.map((e) => ({
      id: e.id,
      title: e.title,
      eventDate: e.event_date,
      startTime: e.start_time,
      category: e.category,
      isForAll: Boolean(e.is_for_all),
      isRelevantToMe:Boolean(e.is_relevant_to_me),
      isAiGenerated: Boolean(e.is_ai_generated),
      createdBy: e.created_by,
      createdAt: e.created_at,
      notes: e.notes,
      circularNumber: e.circular_number ?? null,
      // Un JSON corrotto non deve far sparire l'intero evento dalla lista: si ripiega su
      // "nessun destinatario specifico noto" invece di far fallire la risposta.
      visibleToUserIds: e.visible_to_user_ids_json
        ? (() => {
            try {
              return JSON.parse(e.visible_to_user_ids_json) as string[];
            } catch {
              return null;
            }
          })()
        : null,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/calendar — Crea evento
// ---------------------------------------------------------------------------
calendar.post('/', async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{
    title: string;
    eventDate: string;
    endDate?: string;
    repeat?: 'NONE' | 'DAILY' | 'WEEKLY' | 'CUSTOM';
    customSchedule?: Array<{ weekday: number; time?: string | null }>;
    startTime?: string;
    category: EventCategory;
    isForAll?: boolean;
    isAiGenerated?: boolean;
    circularNumber?: number;
    visibleToUserIds?: string[];
    notes?: string;
    // Inserisci anche se c'e' gia' un evento identico (l'utente ha visto l'avviso e conferma).
    force?: boolean;
  }>();

  const { eventDate, startTime, category, isAiGenerated = false, notes, force = false } = body;
  const endDate = body.endDate ?? eventDate;
  const repeat = body.repeat ?? 'NONE';
  const title = typeof body.title === 'string' ? body.title.trim() : '';
  const visibleToUserIds = Array.isArray(body.visibleToUserIds)
    ? [...new Set(body.visibleToUserIds.filter((id): id is string => typeof id === 'string'))]
    : undefined;
  // Derivato da visibleToUserIds e non dal campo `isForAll` mandato dal client: prima l'app
  // mandava sempre `isForAll` implicito a true (default del repository) anche quando l'utente
  // aveva scelto "Persone specifiche", e l'evento risultava sempre "per tutta la classe".
  const isForAll = visibleToUserIds == null || visibleToUserIds.length === 0;

  if (!title || typeof eventDate !== 'string' || !category) {
    return c.json({ error: 'title, eventDate e category sono obbligatori' }, 400);
  }
  if (!VALID_CATEGORIES.includes(category)) {
    return c.json({ error: `Categoria non valida. Valori ammessi: ${VALID_CATEGORIES.join(', ')}` }, 400);
  }
  if (!isValidIsoDate(eventDate)) {
    return c.json({ error: 'eventDate deve essere nel formato yyyy-mm-dd' }, 400);
  }
  if (!isValidIsoDate(endDate) || endDate < eventDate) {
    return c.json({ error: 'endDate deve essere uguale o successiva a eventDate' }, 400);
  }
  if (!['NONE', 'DAILY', 'WEEKLY', 'CUSTOM'].includes(repeat)) {
    return c.json({ error: 'Ripetizione non valida' }, 400);
  }
  const startUtc = Date.parse(`${eventDate}T00:00:00Z`);
  const endUtc = Date.parse(`${endDate}T00:00:00Z`);
  if ((endUtc - startUtc) / 86_400_000 > 365) return c.json({ error: 'La durata massima è un anno' }, 400);
  if (repeat === 'CUSTOM' && (!Array.isArray(body.customSchedule) || body.customSchedule.length === 0 ||
      new Set(body.customSchedule.map((slot) => slot.weekday)).size !== body.customSchedule.length ||
      body.customSchedule.some((slot) => !Number.isInteger(slot.weekday) || slot.weekday < 0 || slot.weekday > 6 || (slot.time != null && !isValidEventTime(slot.time))))) {
    return c.json({ error: 'Seleziona almeno un giorno e un orario valido' }, 400);
  }
  const eventDates: Array<{ date: string; time: string | null }> = [];
  for (let day = 0; startUtc + day * 86_400_000 <= endUtc; day++) {
    const date = new Date(startUtc + day * 86_400_000).toISOString().slice(0, 10);
    const weekday = (new Date(startUtc + day * 86_400_000).getUTCDay() + 6) % 7;
    if (repeat === 'CUSTOM') {
      const slot = body.customSchedule?.find((item) => item.weekday === weekday);
      if (slot) eventDates.push({ date, time: slot.time ?? null });
    } else if (repeat === 'NONE' || repeat === 'DAILY' || (repeat === 'WEEKLY' && day % 7 === 0)) {
      eventDates.push({ date, time: startTime ?? null });
    }
  }
  if (eventDates.length === 0) return c.json({ error: 'Nessuna occorrenza nell’intervallo selezionato' }, 400);
  if (startTime != null && !isValidEventTime(startTime)) {
    return c.json({ error: 'startTime deve essere HH:MM oppure ora di lezione (es. 3ª ora)' }, 400);
  }
  if (title.length > MAX_TITLE_LENGTH || (notes ?? '').length > MAX_NOTES_LENGTH) {
    return c.json({ error: 'Titolo o note troppo lunghi' }, 400);
  }

  const classId = await resolveClassId(c);

  // I destinatari devono essere della classe.
  if (visibleToUserIds && visibleToUserIds.length > 0) {
    const members = await c.env.DB.prepare('SELECT id FROM users WHERE class_id = ?').bind(classId).all<{ id: string }>();
    const memberIds = new Set(members.results.map((m) => m.id));
    if (visibleToUserIds.some((id) => !memberIds.has(id))) {
      return c.json({ error: 'Alcuni destinatari non fanno parte della classe' }, 400);
    }
  }

  // Doppioni: solo lo stesso evento inserito due volte (stesso giorno, stessa categoria, stesso
  // titolo). Prima bastava una VERIFICA o INTERROGAZIONE qualsiasi entro 3 giorni, di qualunque
  // materia, per bloccare la seconda — e nella stessa settimana (o giorno) di verifiche ce ne
  // sono spesso piu' d'una. L'avviso si puo' superare con `force`.
  if (!force) {
    const sameDay = await c.env.DB.prepare(
      `SELECT id, title FROM calendar_events WHERE class_id = ? AND category = ?
       AND event_date IN (${eventDates.map(() => '?').join(',')})`
    ).bind(classId, category, ...eventDates.map((event) => event.date)).all<{ id: string; title: string }>();
    const wanted = normalizeTitle(title);
    const dup = sameDay.results.find((e) => normalizeTitle(e.title) === wanted);
    if (dup) {
      return c.json({ warning: `C'è già "${dup.title}" in questo giorno. Vuoi aggiungerlo lo stesso?`, existingEventId: dup.id }, 200);
    }
  }

  // Circolare da cui AILA Assistant ha ricavato l'evento: solo se esiste davvero (un numero
  // inventato non deve finire nel calendario di tutta la classe).
  let circularNumber: number | null = null;
  if (isAiGenerated && Number.isInteger(body.circularNumber)) {
    const found = await c.env.DB.prepare('SELECT number FROM circulars WHERE number = ?')
      .bind(body.circularNumber).first<{ number: number }>();
    circularNumber = found?.number ?? null;
  }

  const ids = eventDates.map(() => newUUID());
  const visibleJson = visibleToUserIds && visibleToUserIds.length > 0 ? JSON.stringify(visibleToUserIds) : null;
  const statements = eventDates.map((event, index) => {
    const values = [ids[index], title, event.date, event.time, category, isForAll ? 1 : 0,
      isAiGenerated ? 1 : 0, payload.sub, classId, notes ?? null, visibleJson];
    return circularNumber === null
      ? c.env.DB.prepare(`INSERT INTO calendar_events (id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,notes,visible_to_user_ids_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)`).bind(...values)
      : c.env.DB.prepare(`INSERT INTO calendar_events (id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,notes,visible_to_user_ids_json,circular_number) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`).bind(...values, circularNumber);
  });
  try {
    await c.env.DB.batch(statements);
  } catch (e) {
    if (circularNumber === null) throw e;
    console.error('[Calendar] circular_number non salvato (manca la migrazione 015?)', e);
    await c.env.DB.batch(eventDates.map((event, index) => c.env.DB.prepare(
      `INSERT INTO calendar_events (id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,notes,visible_to_user_ids_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)`
    ).bind(ids[index], title, event.date, event.time, category, isForAll ? 1 : 0, isAiGenerated ? 1 : 0, payload.sub, classId, notes ?? null, visibleJson)));
  }
  return c.json({ success: true, id: ids[0], count: ids.length }, 201);
});

// ---------------------------------------------------------------------------
// PUT /api/calendar/:id — Modifica evento
// ---------------------------------------------------------------------------
calendar.put('/:id', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');

  // Il vincolo di classe non è ridondante: senza, conoscendo (o indovinando) un id si poteva
  // modificare o cancellare l'evento di un'altra classe.
  const classId = await resolveClassId(c);
  const event = await c.env.DB.prepare(
    'SELECT id, created_by, is_ai_generated FROM calendar_events WHERE id = ? AND class_id = ?'
  ).bind(id, classId).first<{ id: string; created_by: string | null; is_ai_generated: number }>();

  if (!event) return c.json({ error: 'Evento non trovato' }, 404);

  // Eventi non-AI: solo chi li ha creati o un rappresentante può modificarli. Gli eventi AI
  // (senza un creatore "umano" specifico) sono modificabili da chiunque nella classe.
  if (!event.is_ai_generated && event.created_by !== payload.sub && payload.role !== 'REPRESENTATIVE') {
    return c.json({ error: 'Puoi modificare solo i tuoi eventi' }, 403);
  }

  const body = await c.req.json<{
    title?: string;
    eventDate?: string;
    startTime?: string | null;
    category?: EventCategory;
    notes?: string | null;
    isForAll?: boolean | null;
    visibleToUserIds?: string[] | null;
  }>();

  if (body.title !== undefined && (typeof body.title !== 'string' || !body.title.trim() || body.title.length > MAX_TITLE_LENGTH)) {
    return c.json({ error: 'Titolo non valido' }, 400);
  }
  if (body.eventDate !== undefined && (typeof body.eventDate !== 'string' || !isValidIsoDate(body.eventDate))) {
    return c.json({ error: 'eventDate deve essere una data valida nel formato yyyy-mm-dd' }, 400);
  }
  if (body.startTime != null && !isValidEventTime(body.startTime)) {
    return c.json({ error: 'startTime deve essere HH:MM oppure ora di lezione (es. 3ª ora)' }, 400);
  }
  if (body.category !== undefined && !VALID_CATEGORIES.includes(body.category)) {
    return c.json({ error: 'Categoria non valida' }, 400);
  }
  if (body.notes != null && (typeof body.notes !== 'string' || body.notes.length > MAX_NOTES_LENGTH)) {
    return c.json({ error: 'Note non valide' }, 400);
  }

  const updateAudience = body.isForAll != null;
  if (updateAudience && typeof body.isForAll !== 'boolean') {
    return c.json({ error: 'Destinatari non validi' }, 400);
  }
  if (body.visibleToUserIds != null && (!Array.isArray(body.visibleToUserIds) ||
      body.visibleToUserIds.some(id => typeof id !== 'string' || !id.trim()))) {
    return c.json({ error: 'Destinatari non validi' }, 400);
  }
  if (!updateAudience && body.visibleToUserIds != null) {
    return c.json({ error: 'Specifica isForAll per modificare i destinatari' }, 400);
  }
  const recipients = [...new Set(body.visibleToUserIds ?? [])];
  if (updateAudience && !body.isForAll && recipients.length === 0) {
    return c.json({ error: 'Seleziona almeno un destinatario' }, 400);
  }
  if (updateAudience && recipients.length > 0) {
    const members = await c.env.DB.prepare('SELECT id FROM users WHERE class_id = ?').bind(classId).all<{id:string}>();
    const ids = new Set(members.results.map(member => member.id));
    if (recipients.some(id => !ids.has(id))) return c.json({ error: 'Alcuni destinatari non fanno parte della classe' }, 400);
  }

  // `startTime: null` e `notes: null` cancellano il valore; un campo assente resta com'e'.
  await c.env.DB.prepare(
    `UPDATE calendar_events SET
       title = COALESCE(?, title),
       event_date = COALESCE(?, event_date),
       start_time = CASE WHEN ? THEN ? ELSE start_time END,
       category = COALESCE(?, category),
       notes = CASE WHEN ? THEN ? ELSE notes END,
       is_for_all = CASE WHEN ? THEN ? ELSE is_for_all END,
       visible_to_user_ids_json = CASE WHEN ? THEN ? ELSE visible_to_user_ids_json END
     WHERE id = ?`
  ).bind(
    body.title?.trim() ?? null,
    body.eventDate ?? null,
    'startTime' in body ? 1 : 0,
    body.startTime ?? null,
    body.category ?? null,
    'notes' in body ? 1 : 0,
    body.notes ?? null,
    updateAudience ? 1 : 0,
    body.isForAll ? 1 : 0,
    updateAudience ? 1 : 0,
    body.isForAll ? null : JSON.stringify(recipients),
    id
  ).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/calendar/:id — Elimina evento
// ---------------------------------------------------------------------------
calendar.delete('/:id', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');

  // Il vincolo di classe non è ridondante: senza, conoscendo (o indovinando) un id si poteva
  // modificare o cancellare l'evento di un'altra classe.
  const classId = await resolveClassId(c);
  const event = await c.env.DB.prepare(
    'SELECT id, created_by, is_ai_generated FROM calendar_events WHERE id = ? AND class_id = ?'
  ).bind(id, classId).first<{ id: string; created_by: string | null; is_ai_generated: number }>();

  if (!event) return c.json({ error: 'Evento non trovato' }, 404);

  // Eventi non-AI: solo chi li ha creati o un rappresentante può eliminarli. Gli eventi AI
  // (senza un creatore "umano" specifico) sono eliminabili da chiunque nella classe.
  if (!event.is_ai_generated && event.created_by !== payload.sub && payload.role !== 'REPRESENTATIVE') {
    return c.json({ error: 'Puoi eliminare solo i tuoi eventi' }, 403);
  }

  await c.env.DB.prepare('DELETE FROM calendar_events WHERE id = ?').bind(id).run();

  return c.json({ success: true });
});

export default calendar;
