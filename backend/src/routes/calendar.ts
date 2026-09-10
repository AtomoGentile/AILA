// =============================================================================
// CIRCOLARE+ — Calendar Routes (/api/calendar/*)
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, EventCategory } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';

const calendar = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

calendar.use('*', authMiddleware());

const VALID_CATEGORIES: EventCategory[] = [
  'VERIFICA', 'INTERROGAZIONE', 'PAGAMENTO', 'USCITA_DIDATTICA', 'AVVISO', 'ALTRO',
];

// ---------------------------------------------------------------------------
// GET /api/calendar — Lista eventi (con filtri)
// ---------------------------------------------------------------------------
calendar.get('/', async (c) => {
  const category = c.req.query('category') as EventCategory | undefined;
  const from = c.req.query('from'); // yyyy-mm-dd
  const to = c.req.query('to');     // yyyy-mm-dd

  // Filtro di classe: senza, il calendario mostrava gli eventi di tutte le classi mescolati.
  const classId = await resolveClassId(c);

  let query = 'SELECT * FROM calendar_events WHERE class_id = ?';
  const params: (string | number)[] = [classId];

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

  query += ' ORDER BY event_date ASC, start_time ASC';

  const rows = await c.env.DB.prepare(query).bind(...params).all<{
    id: string;
    title: string;
    event_date: string;
    start_time: string | null;
    category: string;
    is_for_all: number;
    is_ai_generated: number;
    created_by: string | null;
    created_at: string;
    notes: string | null;
    visible_to_user_ids_json: string | null;
  }>();

  return c.json({
    events: rows.results.map((e) => ({
      id: e.id,
      title: e.title,
      eventDate: e.event_date,
      startTime: e.start_time,
      category: e.category,
      isForAll: Boolean(e.is_for_all),
      isAiGenerated: Boolean(e.is_ai_generated),
      createdBy: e.created_by,
      createdAt: e.created_at,
      notes: e.notes,
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
    startTime?: string;
    category: EventCategory;
    isForAll?: boolean;
    isAiGenerated?: boolean;
    visibleToUserIds?: string[];
    notes?: string;
  }>();

  const {
    title, eventDate, startTime, category, isAiGenerated = false, visibleToUserIds, notes,
  } = body;
  // Derivato da visibleToUserIds e non dal campo `isForAll` mandato dal client: prima l'app
  // mandava sempre `isForAll` implicito a true (default del repository) anche quando l'utente
  // aveva scelto "Persone specifiche", e l'evento risultava sempre "per tutta la classe".
  const isForAll = visibleToUserIds == null || visibleToUserIds.length === 0;

  if (!title || !eventDate || !category) {
    return c.json({ error: 'title, eventDate e category sono obbligatori' }, 400);
  }
  if (!VALID_CATEGORIES.includes(category)) {
    return c.json({ error: `Categoria non valida. Valori ammessi: ${VALID_CATEGORIES.join(', ')}` }, 400);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(eventDate)) {
    return c.json({ error: 'eventDate deve essere nel formato yyyy-mm-dd' }, 400);
  }

  const classId = await resolveClassId(c);

  // Duplicate detection: same category + date within ±3 days (for VERIFICA and INTERROGAZIONE)
  if (category === 'VERIFICA' || category === 'INTERROGAZIONE') {
    const dup = await c.env.DB.prepare(
      `SELECT id FROM calendar_events
       WHERE class_id = ? AND category = ? AND ABS(julianday(event_date) - julianday(?)) <= 3`
    ).bind(classId, category, eventDate).first<{ id: string }>();

    if (dup) {
      return c.json({ warning: 'Potrebbe esserci un evento simile già inserito per questo periodo', existingEventId: dup.id }, 200);
    }
  }

  const id = newUUID();
  await c.env.DB.prepare(
    `INSERT INTO calendar_events
       (id, title, event_date, start_time, category, is_for_all, is_ai_generated, created_by, class_id, notes, visible_to_user_ids_json)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    id, title, eventDate, startTime ?? null, category, isForAll ? 1 : 0, isAiGenerated ? 1 : 0,
    payload.sub, classId,
    notes ?? null,
    visibleToUserIds && visibleToUserIds.length > 0 ? JSON.stringify(visibleToUserIds) : null,
  ).run();

  return c.json({ success: true, id }, 201);
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
    startTime?: string;
    category?: EventCategory;
    isForAll?: boolean;
  }>();

  await c.env.DB.prepare(
    `UPDATE calendar_events SET
       title = COALESCE(?, title),
       event_date = COALESCE(?, event_date),
       start_time = COALESCE(?, start_time),
       category = COALESCE(?, category),
       is_for_all = COALESCE(?, is_for_all)
     WHERE id = ?`
  ).bind(
    body.title ?? null,
    body.eventDate ?? null,
    body.startTime ?? null,
    body.category ?? null,
    body.isForAll !== undefined ? (body.isForAll ? 1 : 0) : null,
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
