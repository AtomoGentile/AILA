// =============================================================================
// CIRCOLARE+ — Ranking Polls Routes (/api/ranking-polls/*)
// Sondaggi a ordinamento: ognuno mette in ordine le opzioni, classifica a punti Borda
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass } from '../services/fcm';

const rankingPolls = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

rankingPolls.use('*', authMiddleware());

const MIN_OPTIONS = 2;
const MAX_OPTIONS = 10;
const MAX_QUESTION_LENGTH = 200;
const MAX_OPTION_LENGTH = 80;
// Lo storico non serve tutto: bastano gli ultimi sondaggi della classe.
const LIST_LIMIT = 30;

// ---------------------------------------------------------------------------
// GET /api/ranking-polls — Lista sondaggi con la propria classifica e i risultati
// ---------------------------------------------------------------------------
//
// I risultati si vedono solo dopo aver inviato la propria classifica (o a sondaggio chiuso):
// vederli prima porterebbe a mettersi dietro alla maggioranza invece di dire cosa si preferisce.
rankingPolls.get('/', async (c) => {
  const payload = c.get('jwtPayload');
  const classId = await resolveClassId(c);
  const db = c.env.DB;

  const [pollRows, optionRows, aggregateRows, countRows, myRows, totalRow] = await Promise.all([
    db.prepare(
      `SELECT id, question, is_closed, created_at
       FROM ranking_polls WHERE class_id = ?
       ORDER BY created_at DESC, rowid DESC LIMIT ?`
    ).bind(classId, LIST_LIMIT).all<{ id: string; question: string; is_closed: number; created_at: string }>(),
    db.prepare(
      `SELECT o.id, o.poll_id, o.label
       FROM ranking_poll_options o JOIN ranking_polls p ON p.id = o.poll_id
       WHERE p.class_id = ?
       ORDER BY o.position ASC`
    ).bind(classId).all<{ id: string; poll_id: string; label: string }>(),
    db.prepare(
      `SELECT a.poll_id, a.option_id,
              SUM(a.rank) AS rank_sum,
              COUNT(*) AS answers,
              SUM(CASE WHEN a.rank = 1 THEN 1 ELSE 0 END) AS first_places
       FROM ranking_poll_answers a JOIN ranking_polls p ON p.id = a.poll_id
       WHERE p.class_id = ?
       GROUP BY a.poll_id, a.option_id`
    ).bind(classId).all<{ poll_id: string; option_id: string; rank_sum: number; answers: number; first_places: number }>(),
    db.prepare(
      `SELECT a.poll_id, COUNT(DISTINCT a.user_id) AS voters
       FROM ranking_poll_answers a JOIN ranking_polls p ON p.id = a.poll_id
       WHERE p.class_id = ?
       GROUP BY a.poll_id`
    ).bind(classId).all<{ poll_id: string; voters: number }>(),
    db.prepare(
      `SELECT a.poll_id, a.option_id, a.rank
       FROM ranking_poll_answers a JOIN ranking_polls p ON p.id = a.poll_id
       WHERE p.class_id = ? AND a.user_id = ?
       ORDER BY a.rank ASC`
    ).bind(classId, payload.sub).all<{ poll_id: string; option_id: string; rank: number }>(),
    db.prepare(
      `SELECT COUNT(*) AS cnt FROM users WHERE class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')`
    ).bind(classId).first<{ cnt: number }>(),
  ]);

  const optionsByPoll = new Map<string, Array<{ id: string; label: string }>>();
  for (const o of optionRows.results) {
    const list = optionsByPoll.get(o.poll_id) ?? [];
    list.push({ id: o.id, label: o.label });
    optionsByPoll.set(o.poll_id, list);
  }

  const aggregatesByOption = new Map<string, { rankSum: number; answers: number; firstPlaces: number }>();
  for (const a of aggregateRows.results) {
    aggregatesByOption.set(a.option_id, { rankSum: a.rank_sum, answers: a.answers, firstPlaces: a.first_places });
  }

  const votersByPoll = new Map(countRows.results.map((r) => [r.poll_id, r.voters]));

  const myRankingByPoll = new Map<string, string[]>();
  for (const r of myRows.results) {
    const list = myRankingByPoll.get(r.poll_id) ?? [];
    list.push(r.option_id);
    myRankingByPoll.set(r.poll_id, list);
  }

  return c.json({
    totalStudents: totalRow?.cnt ?? 0,
    polls: pollRows.results.map((p) => {
      const options = optionsByPoll.get(p.id) ?? [];
      const myRanking = myRankingByPoll.get(p.id) ?? null;
      const isClosed = Boolean(p.is_closed);
      const voters = votersByPoll.get(p.id) ?? 0;
      const canSeeResults = isClosed || myRanking !== null;

      // Borda: con N opzioni la prima di ogni classifica vale N-1 punti, l'ultima 0.
      // Somma dei punti = N * risposte - somma delle posizioni.
      const results = canSeeResults
        ? options
            .map((o) => {
              const agg = aggregatesByOption.get(o.id);
              const answers = agg?.answers ?? 0;
              const rankSum = agg?.rankSum ?? 0;
              return {
                optionId: o.id,
                label: o.label,
                points: options.length * answers - rankSum,
                firstPlaces: agg?.firstPlaces ?? 0,
                averagePosition: answers > 0 ? Math.round((rankSum / answers) * 10) / 10 : null,
              };
            })
            .sort((a, b) => b.points - a.points || b.firstPlaces - a.firstPlaces)
        : null;

      return {
        id: p.id,
        question: p.question,
        isClosed,
        createdAt: p.created_at,
        options,
        voterCount: voters,
        // Punteggio massimo possibile (tutti mettono la stessa opzione per prima): serve al
        // client per disegnare le barre in proporzione.
        maxPoints: voters * Math.max(options.length - 1, 0),
        myRanking,
        results,
      };
    }),
  });
});

// ---------------------------------------------------------------------------
// POST /api/ranking-polls — Crea e pubblica (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
rankingPolls.post('/', requireRole('REPRESENTATIVE'), async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{ question?: string; options?: string[] }>();
  const classId = await resolveClassId(c);

  const question = (body.question ?? '').trim();
  const options = (body.options ?? []).map((o) => (typeof o === 'string' ? o.trim() : '')).filter((o) => o.length > 0);

  if (!question) return c.json({ error: 'Scrivi la domanda' }, 400);
  if (question.length > MAX_QUESTION_LENGTH) {
    return c.json({ error: `La domanda può avere al massimo ${MAX_QUESTION_LENGTH} caratteri` }, 400);
  }
  if (options.length < MIN_OPTIONS || options.length > MAX_OPTIONS) {
    return c.json({ error: `Servono da ${MIN_OPTIONS} a ${MAX_OPTIONS} opzioni` }, 400);
  }
  if (options.some((o) => o.length > MAX_OPTION_LENGTH)) {
    return c.json({ error: `Ogni opzione può avere al massimo ${MAX_OPTION_LENGTH} caratteri` }, 400);
  }
  if (new Set(options.map((o) => o.toLowerCase())).size !== options.length) {
    return c.json({ error: 'Ci sono opzioni ripetute' }, 400);
  }

  const pollId = newUUID();
  await c.env.DB.batch([
    c.env.DB.prepare(
      'INSERT INTO ranking_polls (id, class_id, question, created_by) VALUES (?, ?, ?, ?)'
    ).bind(pollId, classId, question, payload.sub),
    ...options.map((label, index) =>
      c.env.DB.prepare(
        'INSERT INTO ranking_poll_options (id, poll_id, label, position) VALUES (?, ?, ?, ?)'
      ).bind(newUUID(), pollId, label, index)
    ),
  ]);

  await notifyClass(
    c.env,
    'Nuovo sondaggio',
    `Metti in ordine le opzioni: ${question}`,
    { action: 'ranking_poll_published', poll_id: pollId },
    classId
  );

  return c.json({ success: true, id: pollId }, 201);
});

// ---------------------------------------------------------------------------
// PUT /api/ranking-polls/:id/ranking — Invia (o sostituisce) la propria classifica
// ---------------------------------------------------------------------------
rankingPolls.put('/:id/ranking', async (c) => {
  const payload = c.get('jwtPayload');
  const pollId = c.req.param('id');
  const classId = await resolveClassId(c);
  const body = await c.req.json<{ optionIds?: string[] }>();

  const poll = await c.env.DB.prepare(
    'SELECT id, is_closed FROM ranking_polls WHERE id = ? AND class_id = ?'
  ).bind(pollId, classId).first<{ id: string; is_closed: number }>();
  if (!poll) return c.json({ error: 'Sondaggio non trovato' }, 404);
  if (poll.is_closed) return c.json({ error: 'Il sondaggio è chiuso' }, 409);

  const options = await c.env.DB.prepare(
    'SELECT id FROM ranking_poll_options WHERE poll_id = ?'
  ).bind(pollId).all<{ id: string }>();
  const validIds = new Set(options.results.map((o) => o.id));

  // La classifica deve contenere ogni opzione esattamente una volta: niente opzioni lasciate
  // fuori, altrimenti i punti Borda non sarebbero più confrontabili fra un votante e l'altro.
  const ranking = body.optionIds ?? [];
  const isPermutation =
    ranking.length === validIds.size &&
    new Set(ranking).size === ranking.length &&
    ranking.every((id) => validIds.has(id));
  if (!isPermutation) return c.json({ error: 'La classifica deve contenere tutte le opzioni, una volta sola' }, 400);

  await c.env.DB.batch([
    c.env.DB.prepare('DELETE FROM ranking_poll_answers WHERE poll_id = ? AND user_id = ?').bind(pollId, payload.sub),
    ...ranking.map((optionId, index) =>
      c.env.DB.prepare(
        'INSERT INTO ranking_poll_answers (poll_id, option_id, user_id, rank) VALUES (?, ?, ?, ?)'
      ).bind(pollId, optionId, payload.sub, index + 1)
    ),
  ]);

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/ranking-polls/:id/close — Chiude il sondaggio (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
rankingPolls.put('/:id/close', requireRole('REPRESENTATIVE'), async (c) => {
  const pollId = c.req.param('id');
  const result = await c.env.DB.prepare(
    'UPDATE ranking_polls SET is_closed = 1 WHERE id = ? AND class_id = ?'
  ).bind(pollId, await resolveClassId(c)).run();
  if (!result.meta.changes) return c.json({ error: 'Sondaggio non trovato' }, 404);
  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/ranking-polls/:id — Elimina (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
rankingPolls.delete('/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const pollId = c.req.param('id');
  // Opzioni e risposte hanno ON DELETE CASCADE su poll_id.
  const result = await c.env.DB.prepare(
    'DELETE FROM ranking_polls WHERE id = ? AND class_id = ?'
  ).bind(pollId, await resolveClassId(c)).run();
  if (!result.meta.changes) return c.json({ error: 'Sondaggio non trovato' }, 404);
  return c.json({ success: true });
});

export default rankingPolls;
