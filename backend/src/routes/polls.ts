// =============================================================================
// CIRCOLARE+ — Polls Routes (/api/polls/*)
// Sondaggi interrogazioni con budget voti, bonus sacrificio e swap
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, VoteScore } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass, notifyUser, notifyUsers } from '../services/fcm';
import { classMemberIds, effectiveAudience, isInAudience, normalizeAudience, parseAudience } from '../services/audience';
import { inBackground } from '../services/background';
import { fullName } from '../names';
import { previewAssignments } from '../services/pollPreview';
import { computeAndPersistAssignments, isGridCalculated, PollCalculationError } from '../services/pollAssignments';

const polls = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

polls.use('*', authMiddleware());

// Budget constraints per griglia.
//
// Verde e Rosso Chiaro erano limitati a 3 ciascuno. Con una griglia da dieci date o più questo
// significava restare senza opzioni prima di aver espresso un giudizio su tutte le date, e
// l'app finiva per costringere a mettere Giallo su giornate su cui si aveva un'opinione precisa.
// L'unico tetto che serve davvero è quello del Rosso Scuro (-300), che è il veto: se fosse
// illimitato, bloccare tutte le date sarebbe gratis e l'algoritmo non avrebbe più margine.
// (Il client applica la stessa identica regola: un tetto diverso fra i due lato farebbe
// rifiutare dal server voti che l'app aveva appena accettato.)
const VOTE_LIMITS: Record<VoteScore, number> = {
  50: Infinity,   // VERDE: illimitati
  0: Infinity,    // GIALLO: illimitati
  [-60]: Infinity, // ROSSO CHIARO: illimitati
  [-300]: 2,      // ROSSO SCURO (veto): max 2
};

// Frammento SQL interno: le due bind corrispondono allo stesso gridId.
const OPEN_GRID = `NOT EXISTS(SELECT 1 FROM interrogation_calculations WHERE grid_id = ?)
  AND NOT EXISTS(SELECT 1 FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = ?)`;

const ALREADY_CALCULATED = 'Il sondaggio è già chiuso: il calendario delle interrogazioni è stato calcolato';

// ---------------------------------------------------------------------------
// GET /api/polls — Lista griglie
// ---------------------------------------------------------------------------
polls.get('/', async (c) => {
  const payload = c.get('jwtPayload');
  const classId = await resolveClassId(c);
  const members = await classMemberIds(c.env, classId);
  const rows = await c.env.DB.prepare(
    `SELECT g.id, g.subject, g.is_published, g.closes_at, g.created_at, g.audience_json,
            (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = g.id) AS submitted_count,
            (EXISTS(SELECT 1 FROM interrogation_calculations r WHERE r.grid_id = g.id) OR EXISTS(
              SELECT 1 FROM interrogation_assignments a
              JOIN interrogation_slots s ON s.id = a.slot_id
              WHERE s.grid_id = g.id
            )) AS is_calculated
     FROM interrogation_grids g
     WHERE g.class_id = ?
     ORDER BY g.created_at DESC`
  ).bind(classId).all<{
    id: string;
    subject: string;
    is_published: number;
    closes_at: string | null;
    created_at: string;
    audience_json: string | null;
    submitted_count: number;
    is_calculated: number;
  }>();

  // Chi non è fra i destinatari non vede la griglia; il Rappresentante le vede tutte.
  const isRepresentative = payload.role === 'REPRESENTATIVE';
  return c.json({
    polls: rows.results
      .map((p) => ({ ...p, audience: parseAudience(p.audience_json) }))
      .filter((p) => isRepresentative || isInAudience(p.audience, payload.sub))
      .map((p) => ({
        id: p.id,
        subject: p.subject,
        isPublished: Boolean(p.is_published),
        closesAt: p.closes_at,
        createdAt: p.created_at,
        totalStudents: effectiveAudience(p.audience, members).length,
        submittedCount: p.submitted_count,
        isCalculated: Boolean(p.is_calculated),
        audienceUserIds: p.audience,
        isTarget: isInAudience(p.audience, payload.sub),
      })),
  });
});

// ---------------------------------------------------------------------------
// GET /api/polls/:id — Dettaglio griglia con slot e contatori
// ---------------------------------------------------------------------------
polls.get('/:id', async (c) => {
  const id = c.req.param('id');
  const payload = c.get('jwtPayload');

  const classId = await resolveClassId(c);

  const grid = await c.env.DB.prepare(
    `SELECT id, subject, is_published, closes_at, created_at, audience_json
     FROM interrogation_grids WHERE id = ? AND class_id = ?`
  ).bind(id, classId).first<{
    id: string;
    subject: string;
    is_published: number;
    closes_at: string | null;
    created_at: string;
    audience_json: string | null;
  }>();

  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  const audience = parseAudience(grid.audience_json);
  const isTarget = isInAudience(audience, payload.sub);
  if (!isTarget && payload.role !== 'REPRESENTATIVE') return c.json({ error: 'Griglia non trovata' }, 404);

  const slots = await c.env.DB.prepare(
    `SELECT s.id, s.slot_date, s.capacity, s.teacher_mandatory,
            COALESCE(SUM(CASE WHEN v.vote_score > 0 THEN 1 ELSE 0 END), 0) as green_count,
            COALESCE(SUM(CASE WHEN v.vote_score = 0 THEN 1 ELSE 0 END), 0) as yellow_count,
            COALESCE(SUM(CASE WHEN v.vote_score = -60 THEN 1 ELSE 0 END), 0) as red_light_count,
            COALESCE(SUM(CASE WHEN v.vote_score = -300 THEN 1 ELSE 0 END), 0) as red_dark_count,
            mv.vote_score as my_vote
     FROM interrogation_slots s
     LEFT JOIN interrogation_votes v ON v.slot_id = s.id
     LEFT JOIN interrogation_votes mv ON mv.slot_id = s.id AND mv.student_id = ?
     WHERE s.grid_id = ?
     GROUP BY s.id, mv.vote_score
     ORDER BY s.slot_date ASC`
  ).bind(payload.sub, id).all<{
    id: string;
    slot_date: string;
    capacity: number;
    teacher_mandatory: number;
    green_count: number;
    yellow_count: number;
    red_light_count: number;
    red_dark_count: number;
    my_vote: number | null;
  }>();

  // Get my sacrifice bonus for this subject
  const bonus = await c.env.DB.prepare(
    'SELECT bonus_points FROM student_sacrifice_bonus WHERE student_id = ? AND subject = ?'
  ).bind(payload.sub, grid.subject).first<{ bonus_points: number }>();

  // Avanzamento della compilazione. Prima "ho inviato le mie scelte" era solo un flag sul
  // telefono di chi votava: nessuno poteva sapere quanti compagni avessero finito, e quindi
  // l'algoritmo non aveva modo di partire da sé "quando hanno votato tutti".
  const progress = await c.env.DB.prepare(
    `SELECT
       (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = ?) AS submitted_count,
       (SELECT COUNT(*) FROM interrogation_submissions
         WHERE grid_id = ? AND student_id = ?) AS mine`
  ).bind(id, id, payload.sub).first<{
    submitted_count: number;
    mine: number;
  }>();

  const totalStudents = effectiveAudience(audience, await classMemberIds(c.env, classId)).length;
  const submittedCount = progress?.submitted_count ?? 0;
  const isExpired = grid.closes_at !== null && new Date(grid.closes_at).getTime() <= Date.now();

  return c.json({
    id: grid.id,
    subject: grid.subject,
    isPublished: Boolean(grid.is_published),
    createdAt: grid.created_at,
    closesAt: grid.closes_at,
    mySacrificeBonus: bonus?.bonus_points ?? 0,
    totalStudents,
    submittedCount,
    hasSubmitted: (progress?.mine ?? 0) > 0,
    isExpired,
    // Destinatari: `null` = tutta la classe. Chi non è fra i destinatari (può capitare solo al
    // Rappresentante) vede l'avanzamento ma non vota.
    audienceUserIds: audience,
    isTarget,
    // Il Rappresentante può far girare l'algoritmo quando hanno inviato tutti oppure quando il
    // tempo è scaduto: senza la scadenza, un solo compagno che non vota bloccava la classe.
    canRunAssignments: totalStudents > 0 && (submittedCount >= totalStudents || isExpired),
    slots: slots.results.map((s) => ({
      id: s.id,
      slotDate: s.slot_date,
      capacity: s.capacity,
      teacherMandatory: Boolean(s.teacher_mandatory),
      counts: {
        green: s.green_count,
        yellow: s.yellow_count,
        redLight: s.red_light_count,
        redDark: s.red_dark_count,
      },
      myVote: s.my_vote ?? null,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/polls — Crea griglia (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
polls.post('/', requireRole('REPRESENTATIVE'), async (c) => {
  const body = await c.req.json<{
    subject: string;
    slots: Array<{ slotDate: string; capacity: number; teacherMandatory?: boolean }>;
    // Scadenza della compilazione, in ISO 8601. Facoltativa: senza, la griglia resta aperta
    // finché non hanno inviato tutti (comportamento di prima).
    closesAt?: string | null;
    // Chi deve essere interrogato. Vuoto/assente = tutta la classe.
    audienceUserIds?: string[] | null;
  }>();

  const { subject, slots, closesAt } = body;
  const classId = await resolveClassId(c);

  if (!subject || !slots || slots.length === 0) {
    return c.json({ error: 'subject e almeno uno slot sono obbligatori' }, 400);
  }

  const members = await classMemberIds(c.env, classId);
  const audienceCheck = normalizeAudience(body.audienceUserIds, members);
  if (!audienceCheck.ok) return c.json({ error: audienceCheck.error }, 400);
  const audience = audienceCheck.audience;

  // La capienza totale deve bastare per tutti quelli da interrogare.
  const toAssign = effectiveAudience(audience, members).length;
  const totalCapacity = slots.reduce((sum, s) => sum + (s.capacity ?? 1), 0);
  if (totalCapacity < toAssign) {
    return c.json({
      error: `La capienza totale degli slot (${totalCapacity}) deve essere almeno pari alle persone da interrogare (${toAssign})`
    }, 400);
  }

  const gridId = newUUID();

  const statements = [
    c.env.DB.prepare(
      'INSERT INTO interrogation_grids (id, subject, closes_at, class_id, audience_json) VALUES (?, ?, ?, ?, ?)'
    ).bind(gridId, subject, closesAt ?? null, classId, audience ? JSON.stringify(audience) : null),
    ...slots.map((s) =>
      c.env.DB.prepare(
        'INSERT INTO interrogation_slots (id, grid_id, slot_date, capacity, teacher_mandatory) VALUES (?, ?, ?, ?, ?)'
      ).bind(newUUID(), gridId, s.slotDate, s.capacity ?? 1, s.teacherMandatory ? 1 : 0)
    ),
  ];

  await c.env.DB.batch(statements);

  return c.json({ success: true, id: gridId }, 201);
});

// ---------------------------------------------------------------------------
// DELETE /api/polls/:id — Elimina griglia (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
polls.delete('/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const id = c.req.param('id');

  const grid = await c.env.DB.prepare('SELECT id FROM interrogation_grids WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);

  // Slot, voti, assegnazioni e richieste di scambio hanno tutti ON DELETE CASCADE su grid_id
  // (direttamente o via slot_id): basta cancellare la griglia.
  await c.env.DB.prepare('DELETE FROM interrogation_grids WHERE id = ?').bind(id).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/polls/:id/publish — Pubblica griglia + FCM
// ---------------------------------------------------------------------------
polls.put('/:id/publish', requireRole('REPRESENTATIVE'), async (c) => {
  const id = c.req.param('id');

  const classId = await resolveClassId(c);
  const grid = await c.env.DB.prepare('SELECT id, subject, is_published, audience_json FROM interrogation_grids WHERE id = ? AND class_id = ?')
    .bind(id, classId).first<{ id: string; subject: string; is_published: number; audience_json: string | null }>();

  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (grid.is_published) return c.json({ error: 'Griglia già pubblicata' }, 409);

  await c.env.DB.prepare('UPDATE interrogation_grids SET is_published = 1 WHERE id = ?')
    .bind(id).run();

  const publishTitle = 'Nuovo Sondaggio Interrogazioni';
  const publishText = `È disponibile il sondaggio per le interrogazioni di ${grid.subject}. Esprimi le tue preferenze!`;
  const publishData = { action: 'poll_published', poll_id: id ?? '' };
  const audience = parseAudience(grid.audience_json);
  if (audience) {
    inBackground(c, notifyUsers(c.env, audience, publishTitle, publishText, publishData));
  } else {
    inBackground(c, notifyClass(c.env, publishTitle, publishText, publishData, classId));
  }

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/polls/:id/vote — Vota slot (con vincoli budget)
// ---------------------------------------------------------------------------
polls.post('/:id/vote', async (c) => {
  const payload = c.get('jwtPayload');
  const gridId = c.req.param('id');
  const body = await c.req.json<{ slotId: string; voteScore: VoteScore | null }>();

  const { slotId, voteScore } = body;

  // voteScore null = remove vote
  const validScores: (VoteScore | null)[] = [50, 0, -60, -300, null];
  if (!validScores.includes(voteScore)) {
    return c.json({ error: 'voteScore deve essere 50, 0, -60, -300 oppure null (rimuovi voto)' }, 400);
  }

  const classId = await resolveClassId(c);
  const grid = await c.env.DB.prepare(
    'SELECT id, subject, is_published, closes_at, audience_json FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null; audience_json: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia non è ancora pubblicata' }, 403);
  if (!isInAudience(parseAudience(grid.audience_json), payload.sub)) {
    return c.json({ error: 'Questo sondaggio non è rivolto a te' }, 403);
  }

  if (grid.closes_at && new Date(grid.closes_at).getTime() <= Date.now()) {
    return c.json({ error: 'Il tempo per compilare questo sondaggio è scaduto' }, 403);
  }

  if (await isGridCalculated(c.env, gridId)) {
    return c.json({ error: ALREADY_CALCULATED }, 409);
  }

  // Dopo l'invio le scelte si bloccano: chi vuole cambiarle deve prima ritirare l'invio
  // (DELETE /:id/submit). Senza questo controllo "Invia le mie scelte" non vorrebbe dire nulla.
  const submitted = await c.env.DB.prepare(
    'SELECT student_id FROM interrogation_submissions WHERE grid_id = ? AND student_id = ?'
  ).bind(gridId, payload.sub).first<{ student_id: string }>();
  if (submitted) {
    return c.json({ error: 'Hai già inviato le tue scelte. Annulla l\'invio per modificarle.' }, 409);
  }

  const slot = await c.env.DB.prepare('SELECT id FROM interrogation_slots WHERE id = ? AND grid_id = ?')
    .bind(slotId, gridId).first<{ id: string }>();
  if (!slot) return c.json({ error: 'Slot non trovato in questa griglia' }, 404);

  if (voteScore === null) {
    // Remove vote
    await c.env.DB.prepare(`DELETE FROM interrogation_votes WHERE slot_id = ? AND student_id = ? AND ${OPEN_GRID}
      AND NOT EXISTS(SELECT 1 FROM interrogation_submissions WHERE grid_id = ? AND student_id = ?)`)
      .bind(slotId, payload.sub, gridId, gridId, gridId, payload.sub).run();
    if (await isGridCalculated(c.env, gridId)) return c.json({ error: ALREADY_CALCULATED }, 409);
    return c.json({ success: true });
  }

  // Check budget limits (excluding the current slot being updated)
  const currentVoteForThisSlot = await c.env.DB.prepare(
    'SELECT vote_score FROM interrogation_votes WHERE slot_id = ? AND student_id = ?'
  ).bind(slotId, payload.sub).first<{ vote_score: number }>();

  const limit = VOTE_LIMITS[voteScore as VoteScore];
  if (limit !== Infinity && currentVoteForThisSlot?.vote_score !== voteScore) {
    // Count how many slots already have this voteScore
    const usedCount = await c.env.DB.prepare(
      `SELECT COUNT(*) as cnt FROM interrogation_votes iv
       JOIN interrogation_slots s ON s.id = iv.slot_id
       WHERE s.grid_id = ? AND iv.student_id = ? AND iv.vote_score = ? AND iv.slot_id != ?`
    ).bind(gridId, payload.sub, voteScore, slotId).first<{ cnt: number }>();

    if ((usedCount?.cnt ?? 0) >= limit) {
      const scoreLabel: Record<number, string> = {
        50: 'Verde (+50)',
        [-60]: 'Rosso Chiaro (-60)',
        [-300]: 'Rosso Scuro (-300)',
      };
      return c.json({
        error: `Hai già raggiunto il limite di ${limit} voti ${scoreLabel[voteScore] ?? voteScore} per questa griglia`
      }, 400);
    }
  }

  // Upsert vote
  const written = await c.env.DB.prepare(
    `INSERT INTO interrogation_votes (slot_id, student_id, vote_score)
     SELECT ?, ?, ? WHERE ${OPEN_GRID}
     AND NOT EXISTS(SELECT 1 FROM interrogation_submissions WHERE grid_id = ? AND student_id = ?)
     AND EXISTS(SELECT 1 FROM interrogation_grids WHERE id = ? AND (closes_at IS NULL OR julianday(closes_at) > julianday('now')))
     AND (? != -300 OR (SELECT COUNT(*) FROM interrogation_votes v JOIN interrogation_slots s ON s.id = v.slot_id
       WHERE s.grid_id = ? AND v.student_id = ? AND v.vote_score = -300 AND v.slot_id != ?) < 2)
     ON CONFLICT(slot_id, student_id) DO UPDATE SET vote_score = excluded.vote_score`
  ).bind(slotId, payload.sub, voteScore, gridId, gridId, gridId, payload.sub, gridId, voteScore, gridId, payload.sub, slotId).run();
  if (!written.meta.changes) return c.json({ error: 'Il sondaggio o il tuo invio sono cambiati. Aggiorna prima di votare.' }, 409);

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/polls/:id/submit — Invia le proprie scelte in via definitiva
//
// Prima l'invio esisteva solo come flag locale sul telefono: chiudeva il flusso lato studente
// ma il server non ne sapeva nulla, quindi il Rappresentante non poteva vedere chi avesse
// finito e l'algoritmo non poteva partire "quando hanno votato tutti". Ora l'invio è una riga
// su interrogation_submissions e i voti si bloccano finché non lo si annulla.
// ---------------------------------------------------------------------------
polls.post('/:id/submit', async (c) => {
  const payload = c.get('jwtPayload');
  const gridId = c.req.param('id');
  const classId = await resolveClassId(c);

  const grid = await c.env.DB.prepare(
    'SELECT id, subject, is_published, closes_at, audience_json FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null; audience_json: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia non è ancora pubblicata' }, 403);
  const audience = parseAudience(grid.audience_json);
  if (!isInAudience(audience, payload.sub)) {
    return c.json({ error: 'Questo sondaggio non è rivolto a te' }, 403);
  }

  if (await isGridCalculated(c.env, gridId)) {
    return c.json({ error: ALREADY_CALCULATED }, 409);
  }

  const written = await c.env.DB.prepare(
    `INSERT INTO interrogation_submissions (grid_id, student_id, submitted_at)
     SELECT ?, ?, CURRENT_TIMESTAMP WHERE ${OPEN_GRID}
     ON CONFLICT(grid_id, student_id) DO UPDATE SET submitted_at = excluded.submitted_at`
  ).bind(gridId, payload.sub, gridId, gridId).run();
  if (!written.meta.changes) return c.json({ error: ALREADY_CALCULATED }, 409);

  const progress = await c.env.DB.prepare(
    'SELECT COUNT(*) AS submitted_count FROM interrogation_submissions WHERE grid_id = ?'
  ).bind(gridId).first<{ submitted_count: number }>();

  const totalStudents = effectiveAudience(audience, await classMemberIds(c.env, classId)).length;
  const submittedCount = progress?.submitted_count ?? 0;

  // Ultimo della classe a inviare: il sondaggio si chiude da solo, l'algoritmo calcola subito
  // le assegnazioni (stessa logica di /assignments/run) e lo storico ha già un risultato pronto
  // da mostrare, invece di restare "aperto" in attesa che il Rappresentante lo faccia partire
  // a mano.
  if (totalStudents > 0 && submittedCount >= totalStudents) {
    let assignments;
    try { assignments = await computeAndPersistAssignments(c.env, gridId, classId); }
    catch (error) {
      if (error instanceof PollCalculationError) return c.json({ error: error.message }, 409);
      throw error;
    }
    if (assignments === null) return c.json({ success: true, submittedCount, totalStudents });

    const doneTitle = 'Sondaggio completo';
    const doneText = `Tutti hanno inviato le proprie scelte per ${grid.subject}: il calendario è pronto nello storico.`;
    const doneData = { action: 'poll_complete', poll_id: gridId ?? '' };
    if (audience) {
      inBackground(c, notifyUsers(c.env, audience, doneTitle, doneText, doneData));
    } else {
      inBackground(c, notifyClass(c.env, doneTitle, doneText, doneData, classId));
    }
  }

  return c.json({ success: true, submittedCount, totalStudents });
});

// ---------------------------------------------------------------------------
// DELETE /api/polls/:id/submit — Annulla il proprio invio e torna a poter votare
// ---------------------------------------------------------------------------
polls.delete('/:id/submit', async (c) => {
  const payload = c.get('jwtPayload');
  const gridId = c.req.param('id');
  const classId = await resolveClassId(c);

  const grid = await c.env.DB.prepare(
    'SELECT id, closes_at FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; closes_at: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);

  // A tempo scaduto l'invio non si annulla: altrimenti si potrebbero cambiare le scelte dopo
  // aver visto il calendario che ne è uscito.
  if (grid.closes_at && new Date(grid.closes_at).getTime() <= Date.now()) {
    return c.json({ error: 'Il tempo per compilare questo sondaggio è scaduto' }, 403);
  }

  // Calcolato il calendario l'invio non si ritira piu': prima si poteva ritirare, cambiare i voti
  // e reinviare, e il ricalcolo aggiungeva di nuovo il bonus sacrificio.
  if (await isGridCalculated(c.env, gridId)) {
    return c.json({ error: ALREADY_CALCULATED }, 409);
  }

  await c.env.DB.prepare(
    `DELETE FROM interrogation_submissions WHERE grid_id = ? AND student_id = ? AND ${OPEN_GRID}`
  ).bind(gridId, payload.sub, gridId, gridId).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/polls/:id/deadline — Imposta o rimuove la scadenza (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
polls.put('/:id/deadline', requireRole('REPRESENTATIVE'), async (c) => {
  const gridId = c.req.param('id');
  const classId = await resolveClassId(c);
  const { closesAt } = await c.req.json<{ closesAt: string | null }>();

  if (closesAt !== null && Number.isNaN(new Date(closesAt).getTime())) {
    return c.json({ error: 'closesAt deve essere una data ISO 8601 oppure null' }, 400);
  }

  const grid = await c.env.DB.prepare(
    'SELECT id FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);

  await c.env.DB.prepare('UPDATE interrogation_grids SET closes_at = ? WHERE id = ?')
    .bind(closesAt, gridId).run();

  return c.json({ success: true, closesAt });
});

// ---------------------------------------------------------------------------
// GET /api/polls/:id/assignments — Risultati assegnazione (storico: tutta la classe)
// ---------------------------------------------------------------------------
// Anteprima: nessuna scrittura, nessuna notifica, nessun accredito dei bonus.
polls.get('/:id/assignments/preview', requireRole('REPRESENTATIVE'), async (c) => {
  const basis = c.req.query('basis');
  if (basis !== undefined && basis !== 'ORIGINAL' && basis !== 'VOTES_ONLY') return c.json({ error: 'Modalità di confronto non valida' }, 400);
  try {
    const preview = await previewAssignments(c.env, c.req.param('id')!, await resolveClassId(c), basis);
    if (!preview) return c.json({ error: 'Griglia non trovata' }, 404);
    return c.json(preview);
  } catch (error) {
    if (error instanceof PollCalculationError) return c.json({ error: error.message }, 409);
    throw error;
  }
});

// Fotografia originale e scambi successivi: accesso solo al rappresentante della classe.
polls.get('/:id/calculation', requireRole('REPRESENTATIVE'), async (c) => {
  const gridId = c.req.param('id');
  const grid = await c.env.DB.prepare('SELECT id FROM interrogation_grids WHERE id = ? AND class_id = ?')
    .bind(gridId, await resolveClassId(c)).first();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  const row = await c.env.DB.prepare('SELECT * FROM interrogation_calculations WHERE grid_id = ?').bind(gridId).first<{
    algorithm_version: string; input_json: string | null; result_json: string | null;
    rules_json: string | null; calculated_at: string; redacted_students: number;
  }>();
  const events = await c.env.DB.prepare(`SELECT sequence, requester_id, target_id, requester_slot_id, target_slot_id, occurred_at
    FROM interrogation_swap_events WHERE grid_id = ? ORDER BY sequence`).bind(gridId).all();
  return c.json({
    snapshotAvailable: !!row?.input_json,
    algorithmVersion: row?.algorithm_version ?? null,
    calculatedAt: row?.calculated_at ?? null,
    redactedStudents: row?.redacted_students ?? 0,
    input: row?.input_json ? JSON.parse(row.input_json) : null,
    originalResult: row?.result_json ? JSON.parse(row.result_json) : null,
    rules: row?.rules_json ? JSON.parse(row.rules_json) : null,
    swaps: events.results,
  });
});

polls.get('/:id/assignments', async (c) => {
  const gridId = c.req.param('id');
  const payload = c.get('jwtPayload');

  // AND class_id: senza, uno di un'altra classe poteva leggere le assegnazioni altrui.
  const grid = await c.env.DB.prepare(
    'SELECT id, subject, audience_json FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, await resolveClassId(c)).first<{ id: string; subject: string; audience_json: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  // Lo storico e' di tutta la classe, ma chi non era fra i destinatari del sondaggio non lo vede
  // (come nell'elenco, vedi GET /).
  if (payload.role !== 'REPRESENTATIVE' && !isInAudience(parseAudience(grid.audience_json), payload.sub)) {
    return c.json({ error: 'Griglia non trovata' }, 404);
  }

  const assignments = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id, a.student_id, a.assigned_at,
            u.first_name, u.last_name,
            s.slot_date
     FROM interrogation_assignments a
     JOIN users u ON u.id = a.student_id
     JOIN interrogation_slots s ON s.id = a.slot_id
     WHERE s.grid_id = ?
     ORDER BY s.slot_date ASC, lower(u.last_name) ASC`
  ).bind(gridId).all<{
    id: string;
    slot_id: string;
    student_id: string;
    assigned_at: string;
    first_name: string;
    last_name: string;
    slot_date: string;
  }>();

  return c.json({
    subject: grid.subject,
    assignments: assignments.results.map((a) => ({
      id: a.id,
      slotId: a.slot_id,
      slotDate: a.slot_date,
      studentId: a.student_id,
      studentName: fullName(a.first_name, a.last_name),
      assignedAt: a.assigned_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/polls/:id/assignments/run — Calcola assegnazioni (solo REPRESENTATIVE)
// Implementa l'algoritmo di assegnazione con bonus sacrificio
// ---------------------------------------------------------------------------
polls.post('/:id/assignments/run', requireRole('REPRESENTATIVE'), async (c) => {
  const gridId = c.req.param('id');

  const classId = await resolveClassId(c);
  const grid = await c.env.DB.prepare(
    'SELECT id, subject, is_published, closes_at, audience_json FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null; audience_json: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia deve essere pubblicata prima di calcolare le assegnazioni' }, 400);
  // Un calcolo solo per griglia: ogni ricalcolo riapplicava il bonus sacrificio (lo aggiungeva
  // di nuovo a chi era finito su una data rossa, lo azzerava agli altri).
  if (await isGridCalculated(c.env, gridId!)) {
    return c.json({ error: ALREADY_CALCULATED }, 409);
  }

  // L'algoritmo parte quando hanno inviato tutti oppure quando è scaduto il tempo.
  // `?force=1` lo lascia far partire comunque al Rappresentante: serve quando manca qualcuno
  // che si sa che non voterà (assente da settimane, telefono rotto) e non ha senso che la
  // classe resti ferma. Chi non ha votato viene assegnato lo stesso: nel calcolo più sotto
  // vale come se avesse messo Giallo ovunque.
  const force = c.req.query('force') === '1' || c.req.query('force') === 'true';
  const isExpired = grid.closes_at !== null && new Date(grid.closes_at).getTime() <= Date.now();

  if (!force && !isExpired) {
    const progress = await c.env.DB.prepare(
      'SELECT COUNT(*) AS submitted_count FROM interrogation_submissions WHERE grid_id = ?'
    ).bind(gridId).first<{ submitted_count: number }>();

    const totalStudents = effectiveAudience(parseAudience(grid.audience_json), await classMemberIds(c.env, classId)).length;
    const submittedCount = progress?.submitted_count ?? 0;

    if (totalStudents > 0 && submittedCount < totalStudents) {
      return c.json({
        error: `Mancano ancora ${totalStudents - submittedCount} compagni all'appello. ` +
          'Aspetta che inviino le loro scelte, imposta una scadenza, oppure procedi comunque.',
        submittedCount,
        totalStudents,
      }, 409);
    }
  }

  let assignments;
  try { assignments = await computeAndPersistAssignments(c.env, gridId!, classId, force); }
  catch (error) {
    if (error instanceof PollCalculationError) return c.json({ error: error.message }, 409);
    throw error;
  }
  if (assignments === null) return c.json({ error: ALREADY_CALCULATED }, 409);

  return c.json({
    success: true,
    assignmentCount: assignments.length,
    assignments: assignments.map((a) => ({
      studentId: a.studentId,
      slotId: a.slotId,
      score: a.voteScore,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/polls/:id/swap-request — Richiedi scambio posto
// ---------------------------------------------------------------------------
polls.post('/:id/swap-request', async (c) => {
  const payload = c.get('jwtPayload');
  const gridId = c.req.param('id');
  const { targetStudentId } = await c.req.json<{ targetStudentId: string }>();

  if (targetStudentId === payload.sub) {
    return c.json({ error: 'Non puoi richiedere uno scambio con te stesso' }, 400);
  }

  // Check assignments exist
  const myAssignment = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id FROM interrogation_assignments a
     JOIN interrogation_slots s ON s.id = a.slot_id AND s.grid_id = ?
     WHERE a.student_id = ?`
  ).bind(gridId, payload.sub).first<{ id: string; slot_id: string }>();

  const theirAssignment = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id FROM interrogation_assignments a
     JOIN interrogation_slots s ON s.id = a.slot_id AND s.grid_id = ?
     WHERE a.student_id = ?`
  ).bind(gridId, targetStudentId).first<{ id: string; slot_id: string }>();

  if (!myAssignment) return c.json({ error: 'Non hai un posto assegnato in questa griglia' }, 404);
  if (!theirAssignment) return c.json({ error: 'Lo studente target non ha un posto assegnato in questa griglia' }, 404);

  // Check for existing pending swap request
  const existingRequest = await c.env.DB.prepare(
    `SELECT id FROM swap_requests
     WHERE grid_id = ? AND requester_id = ? AND target_id = ? AND status = 'PENDING'`
  ).bind(gridId, payload.sub, targetStudentId).first<{ id: string }>();

  if (existingRequest) {
    return c.json({ error: 'Hai già una richiesta di scambio in attesa con questo studente' }, 409);
  }

  const swapId = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO swap_requests (id, grid_id, requester_id, target_id) VALUES (?, ?, ?, ?)'
  ).bind(swapId, gridId, payload.sub, targetStudentId).run();

  // Notify target student
  inBackground(c, notifyUser(
    c.env,
    targetStudentId,
    'Richiesta di Scambio Posto',
    'Uno studente vuole scambiare il posto di interrogazione con te. Apri l\'app per confermare o rifiutare.',
    { action: 'swap_request', swap_id: swapId, grid_id: gridId }
  ));

  return c.json({ success: true, swapId }, 201);
});

// ---------------------------------------------------------------------------
// PUT /api/polls/swap/:swapId/confirm — Conferma o rifiuta scambio
// ---------------------------------------------------------------------------
polls.put('/swap/:swapId/confirm', async (c) => {
  const payload = c.get('jwtPayload');
  const swapId = c.req.param('swapId');
  const { accept } = await c.req.json<{ accept: boolean }>();

  const swap = await c.env.DB.prepare(
    'SELECT id, grid_id, requester_id, target_id, status FROM swap_requests WHERE id = ?'
  ).bind(swapId).first<{
    id: string;
    grid_id: string;
    requester_id: string;
    target_id: string;
    status: string;
  }>();

  if (!swap) return c.json({ error: 'Richiesta di scambio non trovata' }, 404);
  if (swap.target_id !== payload.sub) return c.json({ error: 'Non sei il destinatario di questa richiesta' }, 403);
  if (swap.status !== 'PENDING') return c.json({ error: 'Questa richiesta non è più in attesa' }, 409);

  if (!accept) {
    const result = await c.env.DB.prepare("UPDATE swap_requests SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'").bind(swapId).run();
    if (!result.meta.changes) return c.json({ error: 'Questa richiesta non è più in attesa' }, 409);
    return c.json({ success: true, accepted: false });
  }

  // Get both assignments
  const myAssignment = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id FROM interrogation_assignments a
     JOIN interrogation_slots s ON s.id = a.slot_id AND s.grid_id = ?
     WHERE a.student_id = ?`
  ).bind(swap.grid_id, swap.target_id).first<{ id: string; slot_id: string }>();

  const theirAssignment = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id FROM interrogation_assignments a
     JOIN interrogation_slots s ON s.id = a.slot_id AND s.grid_id = ?
     WHERE a.student_id = ?`
  ).bind(swap.grid_id, swap.requester_id).first<{ id: string; slot_id: string }>();

  if (!myAssignment || !theirAssignment) {
    return c.json({ error: 'Uno dei due studenti non ha più un posto assegnato' }, 409);
  }

  // Un solo evento può confermare la richiesta; controlla anche che nessun altro
  // scambio abbia spostato i partecipanti durante la lettura delle assegnazioni.
  const eventId = newUUID();
  const ownEvent = 'EXISTS(SELECT 1 FROM interrogation_swap_events WHERE id = ?)';
  const result = await c.env.DB.batch([
    c.env.DB.prepare(`INSERT OR IGNORE INTO interrogation_swap_events
      (id, request_id, grid_id, requester_id, target_id, requester_slot_id, target_slot_id)
      SELECT ?, ?, ?, ?, ?, ?, ? WHERE EXISTS(SELECT 1 FROM swap_requests WHERE id = ? AND status = 'PENDING')
      AND EXISTS(SELECT 1 FROM interrogation_assignments WHERE id = ? AND slot_id = ?)
      AND EXISTS(SELECT 1 FROM interrogation_assignments WHERE id = ? AND slot_id = ?)`)
      .bind(eventId, swapId, swap.grid_id, swap.requester_id, swap.target_id, theirAssignment.slot_id, myAssignment.slot_id,
        swapId, theirAssignment.id, theirAssignment.slot_id, myAssignment.id, myAssignment.slot_id),
    c.env.DB.prepare(`UPDATE interrogation_assignments SET slot_id = ? WHERE id = ? AND ${ownEvent}`)
      .bind(theirAssignment.slot_id, myAssignment.id, eventId),
    c.env.DB.prepare(`UPDATE interrogation_assignments SET slot_id = ? WHERE id = ? AND ${ownEvent}`)
      .bind(myAssignment.slot_id, theirAssignment.id, eventId),
    c.env.DB.prepare(`UPDATE swap_requests SET status = 'ACCEPTED' WHERE id = ? AND ${ownEvent}`).bind(swapId, eventId),
  ]);
  if (!result[0].meta.changes) return c.json({ error: 'Lo scambio è già stato elaborato o i posti sono cambiati. Aggiorna e riprova.' }, 409);

  // Notify requester
  inBackground(c, notifyUser(
    c.env,
    swap.requester_id,
    'Scambio Posto Confermato',
    'Il tuo scambio di posto per l\'interrogazione è stato confermato!',
    { action: 'swap_accepted', swap_id: swapId }
  ));

  return c.json({ success: true, accepted: true });
});

export default polls;
