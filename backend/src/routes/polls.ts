// =============================================================================
// CIRCOLARE+ — Polls Routes (/api/polls/*)
// Sondaggi interrogazioni con budget voti, bonus sacrificio e swap
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, VoteScore } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass, notifyUser } from '../services/fcm';

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
  [-80]: Infinity, // ROSSO CHIARO: illimitati
  [-300]: 2,      // ROSSO SCURO (veto): max 2
};

// ---------------------------------------------------------------------------
// GET /api/polls — Lista griglie
// ---------------------------------------------------------------------------
polls.get('/', async (c) => {
  const classId = await resolveClassId(c);
  const rows = await c.env.DB.prepare(
    `SELECT g.id, g.subject, g.is_published, g.closes_at, g.created_at,
            (SELECT COUNT(*) FROM users
              WHERE class_id = g.class_id AND role IN ('STUDENT', 'REPRESENTATIVE')) AS total_students,
            (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = g.id) AS submitted_count,
            EXISTS(
              SELECT 1 FROM interrogation_assignments a
              JOIN interrogation_slots s ON s.id = a.slot_id
              WHERE s.grid_id = g.id
            ) AS is_calculated
     FROM interrogation_grids g
     WHERE g.class_id = ?
     ORDER BY g.created_at DESC`
  ).bind(classId).all<{
    id: string;
    subject: string;
    is_published: number;
    closes_at: string | null;
    created_at: string;
    total_students: number;
    submitted_count: number;
    is_calculated: number;
  }>();

  return c.json({
    polls: rows.results.map((p) => ({
      id: p.id,
      subject: p.subject,
      isPublished: Boolean(p.is_published),
      closesAt: p.closes_at,
      createdAt: p.created_at,
      totalStudents: p.total_students,
      submittedCount: p.submitted_count,
      isCalculated: Boolean(p.is_calculated),
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
    `SELECT id, subject, is_published, closes_at, created_at
     FROM interrogation_grids WHERE id = ? AND class_id = ?`
  ).bind(id, classId).first<{
    id: string;
    subject: string;
    is_published: number;
    closes_at: string | null;
    created_at: string;
  }>();

  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);

  const slots = await c.env.DB.prepare(
    `SELECT s.id, s.slot_date, s.capacity, s.teacher_mandatory,
            COALESCE(SUM(CASE WHEN v.vote_score > 0 THEN 1 ELSE 0 END), 0) as green_count,
            COALESCE(SUM(CASE WHEN v.vote_score = 0 THEN 1 ELSE 0 END), 0) as yellow_count,
            COALESCE(SUM(CASE WHEN v.vote_score = -80 THEN 1 ELSE 0 END), 0) as red_light_count,
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
       (SELECT COUNT(*) FROM users
         WHERE class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')) AS total_students,
       (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = ?) AS submitted_count,
       (SELECT COUNT(*) FROM interrogation_submissions
         WHERE grid_id = ? AND student_id = ?) AS mine`
  ).bind(classId, id, id, payload.sub).first<{
    total_students: number;
    submitted_count: number;
    mine: number;
  }>();

  const totalStudents = progress?.total_students ?? 0;
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
  }>();

  const { subject, slots, closesAt } = body;
  const classId = await resolveClassId(c);

  if (!subject || !slots || slots.length === 0) {
    return c.json({ error: 'subject e almeno uno slot sono obbligatori' }, 400);
  }

  // Validate total capacity >= number of registered students
  const studentCount = await c.env.DB.prepare(
    "SELECT COUNT(*) as cnt FROM users WHERE role = 'STUDENT' AND class_id = ?"
  ).bind(classId).first<{ cnt: number }>();

  const totalCapacity = slots.reduce((sum, s) => sum + (s.capacity ?? 1), 0);
  if (totalCapacity < (studentCount?.cnt ?? 0)) {
    return c.json({
      error: `La capienza totale degli slot (${totalCapacity}) deve essere >= al numero di studenti (${studentCount?.cnt ?? 0})`
    }, 400);
  }

  const gridId = newUUID();

  const statements = [
    c.env.DB.prepare(
      'INSERT INTO interrogation_grids (id, subject, closes_at, class_id) VALUES (?, ?, ?, ?)'
    ).bind(gridId, subject, closesAt ?? null, classId),
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
  const grid = await c.env.DB.prepare('SELECT id, subject, is_published FROM interrogation_grids WHERE id = ? AND class_id = ?')
    .bind(id, classId).first<{ id: string; subject: string; is_published: number }>();

  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (grid.is_published) return c.json({ error: 'Griglia già pubblicata' }, 409);

  await c.env.DB.prepare('UPDATE interrogation_grids SET is_published = 1 WHERE id = ?')
    .bind(id).run();

  await notifyClass(
    c.env,
    'Nuovo Sondaggio Interrogazioni',
    `È disponibile il sondaggio per le interrogazioni di ${grid.subject}. Esprimi le tue preferenze!`,
    { action: 'poll_published', poll_id: id ?? '' },
    classId
  );

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
  const validScores: (VoteScore | null)[] = [50, 0, -80, -300, null];
  if (!validScores.includes(voteScore)) {
    return c.json({ error: 'voteScore deve essere 50, 0, -80, -300 oppure null (rimuovi voto)' }, 400);
  }

  const classId = await resolveClassId(c);
  const grid = await c.env.DB.prepare(
    'SELECT id, subject, is_published, closes_at FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia non è ancora pubblicata' }, 403);

  if (grid.closes_at && new Date(grid.closes_at).getTime() <= Date.now()) {
    return c.json({ error: 'Il tempo per compilare questo sondaggio è scaduto' }, 403);
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
    await c.env.DB.prepare('DELETE FROM interrogation_votes WHERE slot_id = ? AND student_id = ?')
      .bind(slotId, payload.sub).run();
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
        [-80]: 'Rosso Chiaro (-80)',
        [-300]: 'Rosso Scuro (-300)',
      };
      return c.json({
        error: `Hai già raggiunto il limite di ${limit} voti ${scoreLabel[voteScore] ?? voteScore} per questa griglia`
      }, 400);
    }
  }

  // Upsert vote
  await c.env.DB.prepare(
    `INSERT INTO interrogation_votes (slot_id, student_id, vote_score)
     VALUES (?, ?, ?)
     ON CONFLICT(slot_id, student_id) DO UPDATE SET vote_score = excluded.vote_score`
  ).bind(slotId, payload.sub, voteScore).run();

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
    'SELECT id, subject, is_published, closes_at FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia non è ancora pubblicata' }, 403);

  await c.env.DB.prepare(
    `INSERT INTO interrogation_submissions (grid_id, student_id, submitted_at)
     VALUES (?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(grid_id, student_id) DO UPDATE SET submitted_at = excluded.submitted_at`
  ).bind(gridId, payload.sub).run();

  const progress = await c.env.DB.prepare(
    `SELECT
       (SELECT COUNT(*) FROM users
         WHERE class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')) AS total_students,
       (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = ?) AS submitted_count`
  ).bind(classId, gridId).first<{ total_students: number; submitted_count: number }>();

  const totalStudents = progress?.total_students ?? 0;
  const submittedCount = progress?.submitted_count ?? 0;

  // Ultimo della classe a inviare: il sondaggio si chiude da solo, l'algoritmo calcola subito
  // le assegnazioni (stessa logica di /assignments/run) e lo storico ha già un risultato pronto
  // da mostrare, invece di restare "aperto" in attesa che il Rappresentante lo faccia partire
  // a mano.
  if (totalStudents > 0 && submittedCount >= totalStudents) {
    await computeAndPersistAssignments(c.env, gridId, classId, grid.subject);

    await notifyClass(
      c.env,
      'Sondaggio completo',
      `Tutti hanno inviato le proprie scelte per ${grid.subject}: il calendario è pronto nello storico.`,
      { action: 'poll_complete', poll_id: gridId ?? '' },
      classId
    );
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

  await c.env.DB.prepare(
    'DELETE FROM interrogation_submissions WHERE grid_id = ? AND student_id = ?'
  ).bind(gridId, payload.sub).run();

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
// Calcola e persiste le assegnazioni per una griglia (algoritmo con bonus sacrificio).
// Condivisa fra l'endpoint manuale /assignments/run e l'invio automatico quando l'ultimo
// studente della classe manda le proprie scelte.
// ---------------------------------------------------------------------------
async function computeAndPersistAssignments(
  env: Env,
  gridId: string,
  classId: string,
  subject: string
): Promise<Array<{ studentId: string; slotId: string; voteScore: number }>> {
  const slots = await env.DB.prepare(
    'SELECT id, slot_date, capacity FROM interrogation_slots WHERE grid_id = ? ORDER BY slot_date ASC'
  ).bind(gridId).all<{ id: string; slot_date: string; capacity: number }>();

  const votes = await env.DB.prepare(
    `SELECT iv.student_id, iv.slot_id, iv.vote_score,
            COALESCE(ssb.bonus_points, 0) as sacrifice_bonus
     FROM interrogation_votes iv
     JOIN interrogation_slots s ON s.id = iv.slot_id AND s.grid_id = ?
     LEFT JOIN student_sacrifice_bonus ssb ON ssb.student_id = iv.student_id AND ssb.subject = ?`
  ).bind(gridId, subject).all<{
    student_id: string;
    slot_id: string;
    vote_score: number;
    sacrifice_bonus: number;
  }>();

  // Include anche il Rappresentante: vota ed è conteggiato in "hanno inviato tutti" (vedi
  // progress query più sotto) esattamente come uno STUDENT, quindi deve poter essere assegnato
  // a uno slot come chiunque altro. Escluderlo qui produceva un algoritmo che, in una classe
  // dove il Rappresentante era l'unico ad aver votato (es. durante i test), non assegnava
  // nessuno: zero righe in interrogation_assignments, quindi "isCalculated" restava falso e il
  // sondaggio non si chiudeva mai, nonostante il pulsante "Calcola risultati" rispondesse 200 OK.
  const students = await env.DB.prepare(
    "SELECT id FROM users WHERE role IN ('STUDENT', 'REPRESENTATIVE') AND class_id = ?"
  ).bind(classId).all<{ id: string }>();

  const scoreMatrix: Record<string, Record<string, number>> = {};
  for (const v of votes.results) {
    if (!scoreMatrix[v.student_id]) scoreMatrix[v.student_id] = {};
    const sacrifice = v.vote_score === 50 ? v.sacrifice_bonus : 0;
    scoreMatrix[v.student_id][v.slot_id] = v.vote_score + sacrifice;
  }

  const allStudentIds = students.results.map((s) => s.id);
  const allSlotIds = slots.results.map((s) => s.id);
  for (const sid of allStudentIds) {
    if (!scoreMatrix[sid]) scoreMatrix[sid] = {};
    for (const slotId of allSlotIds) {
      if (scoreMatrix[sid][slotId] === undefined) scoreMatrix[sid][slotId] = 0;
    }
  }

  const slotCapacity = Object.fromEntries(slots.results.map((s) => [s.id, s.capacity]));
  const slotUsed: Record<string, number> = Object.fromEntries(allSlotIds.map((id) => [id, 0]));
  const assignments: Array<{ studentId: string; slotId: string; voteScore: number }> = [];
  const assignedStudents = new Set<string>();

  type Triple = { studentId: string; slotId: string; score: number };
  const triples: Triple[] = [];
  for (const sid of allStudentIds) {
    for (const slotId of allSlotIds) {
      triples.push({ studentId: sid, slotId, score: scoreMatrix[sid][slotId] });
    }
  }
  triples.sort((a, b) => b.score - a.score);

  for (const triple of triples) {
    if (assignedStudents.has(triple.studentId)) continue;
    if (slotUsed[triple.slotId] >= slotCapacity[triple.slotId]) continue;

    assignments.push({
      studentId: triple.studentId,
      slotId: triple.slotId,
      voteScore: scoreMatrix[triple.studentId][triple.slotId],
    });
    assignedStudents.add(triple.studentId);
    slotUsed[triple.slotId]++;
  }

  const deleteStmt = env.DB.prepare('DELETE FROM interrogation_assignments WHERE slot_id IN (SELECT id FROM interrogation_slots WHERE grid_id = ?)').bind(gridId);

  const insertStmts = assignments.map((a) =>
    env.DB.prepare(
      'INSERT INTO interrogation_assignments (id, slot_id, student_id) VALUES (?, ?, ?)'
    ).bind(newUUID(), a.slotId, a.studentId)
  );

  const bonusStmts: D1PreparedStatement[] = [];
  for (const a of assignments) {
    const originalVoteScore = votes.results.find(
      (v) => v.student_id === a.studentId && v.slot_id === a.slotId
    )?.vote_score ?? 0;

    if (originalVoteScore === -80 || originalVoteScore === -300) {
      const bonus = originalVoteScore === -300 ? 250 : 100;
      bonusStmts.push(
        env.DB.prepare(
          `INSERT INTO student_sacrifice_bonus (student_id, subject, bonus_points)
           VALUES (?, ?, ?)
           ON CONFLICT(student_id, subject) DO UPDATE SET bonus_points = bonus_points + excluded.bonus_points`
        ).bind(a.studentId, subject, bonus)
      );
    } else if (originalVoteScore >= 0) {
      bonusStmts.push(
        env.DB.prepare(
          'DELETE FROM student_sacrifice_bonus WHERE student_id = ? AND subject = ?'
        ).bind(a.studentId, subject)
      );
    }
  }

  await env.DB.batch([deleteStmt, ...insertStmts, ...bonusStmts]);

  return assignments;
}

// ---------------------------------------------------------------------------
// GET /api/polls/:id/assignments — Risultati assegnazione (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
polls.get('/:id/assignments', requireRole('REPRESENTATIVE'), async (c) => {
  const gridId = c.req.param('id');

  const grid = await c.env.DB.prepare('SELECT id, subject FROM interrogation_grids WHERE id = ?')
    .bind(gridId).first<{ id: string; subject: string }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);

  const assignments = await c.env.DB.prepare(
    `SELECT a.id, a.slot_id, a.student_id, a.assigned_at,
            u.first_name, u.last_name,
            s.slot_date
     FROM interrogation_assignments a
     JOIN users u ON u.id = a.student_id
     JOIN interrogation_slots s ON s.id = a.slot_id
     WHERE s.grid_id = ?
     ORDER BY s.slot_date ASC, u.last_name ASC`
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
      studentName: `${a.first_name} ${a.last_name}`,
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
    'SELECT id, subject, is_published, closes_at FROM interrogation_grids WHERE id = ? AND class_id = ?'
  ).bind(gridId, classId).first<{ id: string; subject: string; is_published: number; closes_at: string | null }>();
  if (!grid) return c.json({ error: 'Griglia non trovata' }, 404);
  if (!grid.is_published) return c.json({ error: 'La griglia deve essere pubblicata prima di calcolare le assegnazioni' }, 400);

  // L'algoritmo parte quando hanno inviato tutti oppure quando è scaduto il tempo.
  // `?force=1` lo lascia far partire comunque al Rappresentante: serve quando manca qualcuno
  // che si sa che non voterà (assente da settimane, telefono rotto) e non ha senso che la
  // classe resti ferma. Chi non ha votato viene assegnato lo stesso: nel calcolo più sotto
  // vale come se avesse messo Giallo ovunque.
  const force = c.req.query('force') === '1' || c.req.query('force') === 'true';
  const isExpired = grid.closes_at !== null && new Date(grid.closes_at).getTime() <= Date.now();

  if (!force && !isExpired) {
    const progress = await c.env.DB.prepare(
      `SELECT
         (SELECT COUNT(*) FROM users
           WHERE class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')) AS total_students,
         (SELECT COUNT(*) FROM interrogation_submissions WHERE grid_id = ?) AS submitted_count`
    ).bind(classId, gridId).first<{ total_students: number; submitted_count: number }>();

    const totalStudents = progress?.total_students ?? 0;
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

  const assignments = await computeAndPersistAssignments(c.env, gridId!, classId, grid.subject);

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
  await notifyUser(
    c.env,
    targetStudentId,
    'Richiesta di Scambio Posto',
    'Uno studente vuole scambiare il posto di interrogazione con te. Apri l\'app per confermare o rifiutare.',
    { action: 'swap_request', swap_id: swapId, grid_id: gridId }
  );

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
    await c.env.DB.prepare("UPDATE swap_requests SET status = 'REJECTED' WHERE id = ?").bind(swapId).run();
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

  // Swap slots
  await c.env.DB.batch([
    c.env.DB.prepare('UPDATE interrogation_assignments SET slot_id = ? WHERE id = ?')
      .bind(theirAssignment.slot_id, myAssignment.id),
    c.env.DB.prepare('UPDATE interrogation_assignments SET slot_id = ? WHERE id = ?')
      .bind(myAssignment.slot_id, theirAssignment.id),
    c.env.DB.prepare("UPDATE swap_requests SET status = 'ACCEPTED' WHERE id = ?").bind(swapId),
  ]);

  // Notify requester
  await notifyUser(
    c.env,
    swap.requester_id,
    'Scambio Posto Confermato',
    'Il tuo scambio di posto per l\'interrogazione è stato confermato!',
    { action: 'swap_accepted', swap_id: swapId }
  );

  return c.json({ success: true, accepted: true });
});

export default polls;
