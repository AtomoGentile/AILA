// =============================================================================
// CIRCOLARE+ — Preferences Routes
// /api/config/preferences — stato raccolta (toggle rappresentante)
// /api/preferences/* — votazione preferenze sociali compagni
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, resolveClassId, ensureClassRow } from '../auth';
import { notifyClass, notifyUser } from '../services/fcm';
import { inBackground } from '../services/background';

const preferences = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

preferences.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// Avanzamento della raccolta: quanti compagni hanno espresso almeno una preferenza.
//
// "Ha votato" = ha almeno una riga in social_preferences (anche un voto neutro): non esiste un
// "invio" separato come nei sondaggi, il voto si salva a ogni tocco. Guardie di sicurezza e altri
// ruoli non votano e non entrano nel conteggio.
// ---------------------------------------------------------------------------
async function preferencesProgress(env: Env, classId: string): Promise<{
  totalStudents: number;
  votedCount: number;
  pending: Array<{ id: string; firstName: string; lastName: string }>;
}> {
  const rows = await env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name,
            EXISTS (SELECT 1 FROM social_preferences sp WHERE sp.from_student_id = u.id) AS has_voted
     FROM users u
     WHERE u.class_id = ? AND u.role IN ('STUDENT', 'REPRESENTATIVE')
     ORDER BY u.last_name, u.first_name`
  ).bind(classId).all<{ id: string; first_name: string; last_name: string; has_voted: number }>();

  const pending = rows.results
    .filter((r) => !r.has_voted)
    .map((r) => ({ id: r.id, firstName: r.first_name, lastName: r.last_name }));

  return {
    totalStudents: rows.results.length,
    votedCount: rows.results.length - pending.length,
    pending,
  };
}

// ---------------------------------------------------------------------------
// GET /api/preferences/progress — Quanti hanno già votato e quanti no
// I nomi di chi manca li vede solo il Rappresentante, che è chi può sollecitarli.
// ---------------------------------------------------------------------------
preferences.get('/progress', async (c) => {
  const payload = c.get('jwtPayload');
  const classId = await resolveClassId(c);
  const progress = await preferencesProgress(c.env, classId);

  return c.json({
    totalStudents: progress.totalStudents,
    votedCount: progress.votedCount,
    pendingCount: progress.totalStudents - progress.votedCount,
    allVoted: progress.totalStudents > 0 && progress.votedCount >= progress.totalStudents,
    pending: payload.role === 'REPRESENTATIVE' ? progress.pending : [],
  });
});

// ---------------------------------------------------------------------------
// GET /api/config/preferences — Stato raccolta
// ---------------------------------------------------------------------------
preferences.get('/config', async (c) => {
  // Era letto da app_config con la costante 'DEFAULT_CLASS': una sola finestra preferenze per
  // tutta l'app, quindi il Rappresentante di una classe la apriva anche a tutte le altre.
  const classId = await resolveClassId(c);
  // Autoripara un classId "orfano" (utenti con class_id valorizzato ma nessuna riga `classes`
  // corrispondente): vedi il commento su ensureClassRow per perché serve e cosa causava.
  await ensureClassRow(c.env, classId);
  const config = await c.env.DB.prepare(
    'SELECT preferences_open, label, created_at FROM classes WHERE id = ?'
  ).bind(classId).first<{ preferences_open: number; label: string; created_at: string }>();

  return c.json({
    preferencesOpen: Boolean(config?.preferences_open ?? 0),
    classId,
    classLabel: config?.label ?? null,
    updatedAt: config?.created_at ?? null,
  });
});

// ---------------------------------------------------------------------------
// POST /api/config/preferences — Apri/chiudi raccolta (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
preferences.post('/config', requireRole('REPRESENTATIVE'), async (c) => {
  const { open } = await c.req.json<{ open: boolean }>();

  const classId = await resolveClassId(c);
  // Stessa autoriparazione di GET /config: senza, l'UPDATE sotto colpirebbe zero righe per un
  // classId "orfano" e la finestra preferenze resterebbe "chiusa" per sempre, qualunque cosa si
  // clicchi — nessun errore visibile, perché un UPDATE senza righe corrispondenti "riesce" comunque.
  await ensureClassRow(c.env, classId);

  await c.env.DB.prepare(
    'UPDATE classes SET preferences_open = ? WHERE id = ?'
  ).bind(open ? 1 : 0, classId).run();

  // app_config resta allineato per non rompere eventuali strumenti che leggono ancora di lì.
  await c.env.DB.prepare(
    'UPDATE app_config SET preferences_open = ?, updated_at = CURRENT_TIMESTAMP WHERE class_id = ?'
  ).bind(open ? 1 : 0, classId).run();

  if (open) {
    inBackground(c, notifyClass(
      c.env,
      'Mappa Posti — Esprimi le tue preferenze',
      'Il Rappresentante ha aperto la finestra per esprimere le preferenze sui compagni di banco!',
      { action: 'open_preferences' },
      classId
    ));
  }

  return c.json({ success: true, preferencesOpen: open });
});

// ---------------------------------------------------------------------------
// POST /api/preferences/vote — Esprimi preferenza su un compagno
// Vincoli anti-gaming:
//   - Max 2 voti +2
//   - Max 2 voti -2
//   - Non puoi votare te stesso
//   - La raccolta deve essere aperta
// ---------------------------------------------------------------------------
preferences.post('/vote', async (c) => {
  const payload = c.get('jwtPayload');
  const { toStudentId, score } = await c.req.json<{ toStudentId: string; score: -2 | -1 | 0 | 1 | 2 }>();

  const validScores = [-2, -1, 0, 1, 2];
  if (!validScores.includes(score)) {
    return c.json({ error: 'score deve essere uno tra: -2, -1, 0, 1, 2' }, 400);
  }

  if (toStudentId === payload.sub) {
    return c.json({ error: 'Non puoi votare te stesso' }, 400);
  }

  const classId = await resolveClassId(c);

  // Check preferences window is open (per la propria classe)
  const config = await c.env.DB.prepare(
    'SELECT preferences_open FROM classes WHERE id = ?'
  ).bind(classId).first<{ preferences_open: number }>();

  if (!config?.preferences_open) {
    return c.json({ error: 'La raccolta preferenze è chiusa' }, 403);
  }

  // Il compagno votato dev'essere della stessa classe: le preferenze alimentano la disposizione
  // dei banchi di quell'aula, un voto verso un'altra classe non significherebbe nulla.
  const target = await c.env.DB.prepare('SELECT id FROM users WHERE id = ? AND class_id = ?')
    .bind(toStudentId, classId).first<{ id: string }>();
  if (!target) return c.json({ error: 'Studente non trovato in questa classe' }, 404);

  // Anti-gaming: check current extreme vote counts
  if (score === 2 || score === -2) {
    const extremeCount = await c.env.DB.prepare(
      'SELECT COUNT(*) as cnt FROM social_preferences WHERE from_student_id = ? AND score = ?'
    ).bind(payload.sub, score).first<{ cnt: number }>();

    // Check if this is replacing an existing vote
    const existingVote = await c.env.DB.prepare(
      'SELECT score FROM social_preferences WHERE from_student_id = ? AND to_student_id = ?'
    ).bind(payload.sub, toStudentId).first<{ score: number }>();

    const alreadyHasThisExtremeToThisUser = existingVote?.score === score;
    const currentCount = extremeCount?.cnt ?? 0;

    if (!alreadyHasThisExtremeToThisUser && currentCount >= 2) {
      return c.json({
        error: `Hai già espresso 2 voti ${score > 0 ? '+2' : '-2'}. Revoca uno prima di aggiungerne un altro.`
      }, 400);
    }
  }

  // Serve a capire se questo e' il PRIMO voto dello studente: solo allora puo' essere quello che
  // completa la classe. Ricontare a ogni voto notificherebbe il Rappresentante decine di volte.
  const priorVote = await c.env.DB.prepare(
    'SELECT 1 AS present FROM social_preferences WHERE from_student_id = ? LIMIT 1'
  ).bind(payload.sub).first<{ present: number }>();

  // Upsert
  await c.env.DB.prepare(
    `INSERT INTO social_preferences (from_student_id, to_student_id, score, updated_at)
     VALUES (?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(from_student_id, to_student_id) DO UPDATE SET score = excluded.score, updated_at = excluded.updated_at`
  ).bind(payload.sub, toStudentId, score).run();

  if (!priorVote) {
    const progress = await preferencesProgress(c.env, classId);
    if (progress.totalStudents > 0 && progress.votedCount >= progress.totalStudents) {
      // Tutti hanno votato: si avvisano i Rappresentanti della classe, che sono quelli che
      // possono generare la disposizione.
      const reps = await c.env.DB.prepare(
        "SELECT id FROM users WHERE class_id = ? AND role = 'REPRESENTATIVE'"
      ).bind(classId).all<{ id: string }>();
      await Promise.allSettled(
        reps.results.map((rep) =>
          notifyUser(
            c.env,
            rep.id,
            'Mappa Posti — Hanno votato tutti',
            `Tutta la classe (${progress.votedCount}/${progress.totalStudents}) ha espresso le preferenze: puoi generare la disposizione.`,
            { action: 'preferences_complete' }
          )
        )
      );
    }
  }

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// GET /api/preferences/my — Le preferenze espresse dall'utente corrente
// ---------------------------------------------------------------------------
preferences.get('/my', async (c) => {
  const payload = c.get('jwtPayload');

  const rows = await c.env.DB.prepare(
    `SELECT sp.to_student_id, sp.score, sp.updated_at, u.first_name, u.last_name
     FROM social_preferences sp
     JOIN users u ON u.id = sp.to_student_id
     WHERE sp.from_student_id = ?
     ORDER BY sp.updated_at DESC`
  ).bind(payload.sub).all<{
    to_student_id: string;
    score: number;
    updated_at: string;
    first_name: string;
    last_name: string;
  }>();

  return c.json({
    votes: rows.results.map((r) => ({
      toStudentId: r.to_student_id,
      toName: `${r.first_name} ${r.last_name}`,
      score: r.score,
      updatedAt: r.updated_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// GET /api/preferences/summary — Matrice aggregata (solo REPRESENTATIVE)
// Restituisce i punteggi aggregati per l'algoritmo SeatMapOptimizer
// NON espone i voti individuali -2 (blindati)
// ---------------------------------------------------------------------------
preferences.get('/summary', requireRole('REPRESENTATIVE'), async (c) => {
  // social_preferences non ha una colonna classe: la classe arriva da chi ha votato.
  const rows = await c.env.DB.prepare(
    `SELECT sp.from_student_id, sp.to_student_id, sp.score
     FROM social_preferences sp
     JOIN users u ON u.id = sp.from_student_id
     WHERE u.class_id = ?`
  ).bind(await resolveClassId(c)).all<{ from_student_id: string; to_student_id: string; score: number }>();

  // Blind -2 votes: never expose to representative, only used by algorithm
  // Return only aggregate counts per student pair (not individual scores)
  const pairMap: Record<string, { positive: number; negative: number; neutral: number }> = {};

  for (const row of rows.results) {
    const key = [row.from_student_id, row.to_student_id].sort().join(':');
    if (!pairMap[key]) pairMap[key] = { positive: 0, negative: 0, neutral: 0 };
    if (row.score > 0) pairMap[key].positive++;
    else if (row.score < 0) pairMap[key].negative++;
    else pairMap[key].neutral++;
  }

  return c.json({ summary: pairMap });
});

// ---------------------------------------------------------------------------
// GET /api/preferences/matrix — Matrice completa per algoritmo (solo REPRESENTATIVE)
// Espone tutti i voti inclusi i -2 SOLO per uso algoritmico, non in UI
// ---------------------------------------------------------------------------
preferences.get('/matrix', requireRole('REPRESENTATIVE'), async (c) => {
  const rows = await c.env.DB.prepare(
    `SELECT sp.from_student_id, sp.to_student_id, sp.score
     FROM social_preferences sp
     JOIN users u ON u.id = sp.from_student_id
     WHERE u.class_id = ?`
  ).bind(await resolveClassId(c)).all<{ from_student_id: string; to_student_id: string; score: number }>();

  return c.json({
    matrix: rows.results.map((r) => ({
      from: r.from_student_id,
      to: r.to_student_id,
      score: r.score,
    })),
  });
});

export default preferences;
