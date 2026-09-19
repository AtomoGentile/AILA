// =============================================================================
// CIRCOLARE+ — Proposals Routes (/api/proposals/*)
// Bacheca proposte con anonimato, voti +1/-1, commenti e quorum sblocco
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload, ProposalStatus } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass } from '../services/fcm';

const proposals = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

proposals.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// GET /api/proposals — Lista proposte (filtrate per status)
// ---------------------------------------------------------------------------
proposals.get('/', async (c) => {
  const status = c.req.query('status') as ProposalStatus | undefined;
  const payload = c.get('jwtPayload');
  const isRep = payload.role === 'REPRESENTATIVE';

  let query = `
    SELECT p.id, p.is_anonymous, p.title, p.description, p.category, p.status,
           p.modified_by_rep, p.edited_at, p.created_at, p.author_id,
           u.first_name as author_first_name, u.last_name as author_last_name,
           COALESCE(vup.cnt, 0) as up_votes,
           COALESCE(vdown.cnt, 0) as down_votes,
           COALESCE(cmt.cnt, 0) as comment_count,
           mv.vote_type as my_vote
    FROM proposals p
    JOIN users u ON u.id = p.author_id
    LEFT JOIN (SELECT proposal_id, COUNT(*) as cnt FROM proposal_votes WHERE vote_type = 1 GROUP BY proposal_id) vup ON vup.proposal_id = p.id
    LEFT JOIN (SELECT proposal_id, COUNT(*) as cnt FROM proposal_votes WHERE vote_type = -1 GROUP BY proposal_id) vdown ON vdown.proposal_id = p.id
    LEFT JOIN (SELECT proposal_id, COUNT(*) as cnt FROM proposal_comments GROUP BY proposal_id) cmt ON cmt.proposal_id = p.id
    LEFT JOIN proposal_votes mv ON mv.proposal_id = p.id AND mv.user_id = ?
    WHERE p.class_id = ?
  `;
  // La bacheca è della classe: senza questo filtro ognuno leggeva le proposte di tutte.
  const params: (string | number)[] = [payload.sub, await resolveClassId(c)];

  if (status) {
    query += ' AND p.status = ?';
    params.push(status);
  }

  query += ' ORDER BY p.created_at DESC';

  const rows = await c.env.DB.prepare(query).bind(...params).all<{
    id: string;
    is_anonymous: number;
    title: string;
    description: string;
    category: string;
    status: string;
    modified_by_rep: number;
    edited_at: string | null;
    created_at: string;
    author_id: string;
    author_first_name: string;
    author_last_name: string;
    up_votes: number;
    down_votes: number;
    comment_count: number;
    my_vote: number | null;
  }>();

  return c.json({
    proposals: rows.results.map((p) => {
      // Reveal author (id + name) only to representative, to the author themselves, or when not anonymous
      const canSeeAuthor = !p.is_anonymous || isRep || p.author_id === payload.sub;
      return {
        id: p.id,
        title: p.title,
        description: p.description,
        category: p.category,
        status: p.status,
        isAnonymous: Boolean(p.is_anonymous),
        authorId: canSeeAuthor ? p.author_id : null,
        authorName: canSeeAuthor ? `${p.author_first_name} ${p.author_last_name}` : null,
        // Volutamente "una qualunque modifica", non solo quelle del Rappresentante: il nome
        // del campo resta per non rompere le app già installate, ma in bacheca ciò che serve
        // sapere è che il testo non è più quello pubblicato all'inizio. `editedByRepresentative`
        // conserva la distinzione per quando servirà.
        modifiedByRep: Boolean(p.modified_by_rep) || p.edited_at !== null,
        editedByRepresentative: Boolean(p.modified_by_rep),
        editedAt: p.edited_at,
        upVotes: p.up_votes,
        downVotes: p.down_votes,
        commentCount: p.comment_count,
        myVote: p.my_vote ?? null,
        createdAt: p.created_at,
      };
    }),
  });
});

// ---------------------------------------------------------------------------
// POST /api/proposals — Crea proposta
// ---------------------------------------------------------------------------
proposals.post('/', async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{
    title: string;
    description: string;
    category?: string;
    isAnonymous?: boolean;
  }>();

  const { title, description, category = 'GENERALE', isAnonymous = false } = body;

  if (!title || !description) {
    return c.json({ error: 'title e description sono obbligatori' }, 400);
  }
  if (title.length > 200) {
    return c.json({ error: 'Il titolo non può superare 200 caratteri' }, 400);
  }

  const id = newUUID();
  const classId = await resolveClassId(c);
  await c.env.DB.prepare(
    'INSERT INTO proposals (id, author_id, is_anonymous, title, description, category, class_id) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, payload.sub, isAnonymous ? 1 : 0, title, description, category, classId).run();

  // Notify users who have board notifications enabled
  await notifyClass(c.env, 'Nuova Proposta in Bacheca', title, { action: 'new_proposal', proposal_id: id }, classId);

  return c.json({ success: true, id }, 201);
});

// ---------------------------------------------------------------------------
// PUT /api/proposals/:id — Modifica testo (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
//
// Chi può modificare: l'autore la propria proposta, il Rappresentante qualunque proposta della
// sua classe. Prima era riservata al solo Rappresentante, quindi chi scriveva una proposta con
// un errore non aveva altra scelta che cancellarla e riscriverla, perdendo voti e commenti.
proposals.put('/:id', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');
  const classId = await resolveClassId(c);

  const proposal = await c.env.DB.prepare(
    'SELECT id, author_id FROM proposals WHERE id = ? AND class_id = ?'
  ).bind(id, classId).first<{ id: string; author_id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const isAuthor = proposal.author_id === payload.sub;
  const isRep = payload.role === 'REPRESENTATIVE';
  if (!isAuthor && !isRep) {
    return c.json({ error: 'Puoi modificare solo le tue proposte' }, 403);
  }

  const body = await c.req.json<{ title?: string; description?: string; category?: string }>();
  if (!body.title && !body.description && !body.category) {
    return c.json({ error: 'Nessuna modifica da salvare' }, 400);
  }

  // modified_by_rep si accende solo quando a modificare è il Rappresentante su una proposta non
  // sua: è un'informazione diversa da "è stata modificata", e in bacheca si legge diversamente.
  const markRep = isRep && !isAuthor ? 1 : 0;

  await c.env.DB.prepare(
    `UPDATE proposals SET
       title = COALESCE(?, title),
       description = COALESCE(?, description),
       category = COALESCE(?, category),
       modified_by_rep = MAX(modified_by_rep, ?),
       edited_at = CURRENT_TIMESTAMP
     WHERE id = ?`
  ).bind(body.title ?? null, body.description ?? null, body.category ?? null, markRep, id).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/proposals/:id/status — Cambia stato (solo REPRESENTATIVE)
// ---------------------------------------------------------------------------
proposals.put('/:id/status', requireRole('REPRESENTATIVE'), async (c) => {
  const id = c.req.param('id');
  const { status } = await c.req.json<{ status: ProposalStatus }>();

  const validStatuses: ProposalStatus[] = ['NUOVA', 'IN_ANALISI', 'CHIUSA'];
  if (!validStatuses.includes(status)) {
    return c.json({ error: `Status non valido. Valori ammessi: ${validStatuses.join(', ')}` }, 400);
  }

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ?')
    .bind(id).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  await c.env.DB.prepare('UPDATE proposals SET status = ? WHERE id = ?')
    .bind(status, id).run();

  return c.json({ success: true, status });
});

// ---------------------------------------------------------------------------
// DELETE /api/proposals/:id — Elimina proposta (autore o REPRESENTATIVE)
// ---------------------------------------------------------------------------
proposals.delete('/:id', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');

  const proposal = await c.env.DB.prepare('SELECT id, author_id FROM proposals WHERE id = ?')
    .bind(id).first<{ id: string; author_id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const isAuthor = proposal.author_id === payload.sub;
  const isRep = payload.role === 'REPRESENTATIVE';
  if (!isAuthor && !isRep) {
    return c.json({ error: 'Puoi eliminare solo le tue proposte' }, 403);
  }

  // voti e commenti hanno ON DELETE CASCADE su proposal_id nello schema: basta cancellare la riga.
  await c.env.DB.prepare('DELETE FROM proposals WHERE id = ?').bind(id).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// POST /api/proposals/:id/vote — Vota proposta (+1 / -1)
// ---------------------------------------------------------------------------
proposals.post('/:id/vote', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');
  const { voteType } = await c.req.json<{ voteType: 1 | -1 | 0 }>();

  if (voteType !== 1 && voteType !== -1 && voteType !== 0) {
    return c.json({ error: 'voteType deve essere 1, -1 oppure 0 (rimuovi voto)' }, 400);
  }

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ?')
    .bind(id).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  if (voteType === 0) {
    // Remove vote
    await c.env.DB.prepare('DELETE FROM proposal_votes WHERE proposal_id = ? AND user_id = ?')
      .bind(id, payload.sub).run();
  } else {
    // Upsert vote
    await c.env.DB.prepare(
      'INSERT INTO proposal_votes (proposal_id, user_id, vote_type) VALUES (?, ?, ?) ON CONFLICT(proposal_id, user_id) DO UPDATE SET vote_type = excluded.vote_type'
    ).bind(id, payload.sub, voteType).run();
  }

  // Return updated counts
  const counts = await c.env.DB.prepare(
    `SELECT
       SUM(CASE WHEN vote_type = 1 THEN 1 ELSE 0 END) as up_votes,
       SUM(CASE WHEN vote_type = -1 THEN 1 ELSE 0 END) as down_votes
     FROM proposal_votes WHERE proposal_id = ?`
  ).bind(id).first<{ up_votes: number; down_votes: number }>();

  return c.json({ success: true, upVotes: counts?.up_votes ?? 0, downVotes: counts?.down_votes ?? 0 });
});

// ---------------------------------------------------------------------------
// GET /api/proposals/:id/comments — Lista commenti
// ---------------------------------------------------------------------------
proposals.get('/:id/comments', async (c) => {
  const id = c.req.param('id');
  const payload = c.get('jwtPayload');

  const proposal = await c.env.DB.prepare('SELECT id, is_anonymous, author_id FROM proposals WHERE id = ?')
    .bind(id).first<{ id: string; is_anonymous: number; author_id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const rows = await c.env.DB.prepare(
    `SELECT c.id, c.user_id, c.content, c.created_at,
            u.first_name, u.last_name
     FROM proposal_comments c
     JOIN users u ON u.id = c.user_id
     WHERE c.proposal_id = ?
     ORDER BY c.created_at ASC`
  ).bind(id).all<{
    id: string;
    user_id: string;
    content: string;
    created_at: string;
    first_name: string;
    last_name: string;
  }>();

  return c.json({
    comments: rows.results.map((cmt) => ({
      id: cmt.id,
      userId: cmt.user_id,
      authorName: `${cmt.first_name} ${cmt.last_name}`,
      content: cmt.content,
      createdAt: cmt.created_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/proposals/:id/comments — Aggiungi commento
// ---------------------------------------------------------------------------
proposals.post('/:id/comments', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');
  const { content } = await c.req.json<{ content: string }>();

  if (!content || content.trim().length === 0) {
    return c.json({ error: 'Il commento non può essere vuoto' }, 400);
  }
  if (content.length > 1000) {
    return c.json({ error: 'Il commento non può superare 1000 caratteri' }, 400);
  }

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ?')
    .bind(id).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const commentId = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO proposal_comments (id, proposal_id, user_id, content) VALUES (?, ?, ?, ?)'
  ).bind(commentId, id, payload.sub, content.trim()).run();

  return c.json({ success: true, id: commentId }, 201);
});

// ---------------------------------------------------------------------------
// POST /api/proposals/:id/unlock-identity — Sblocco identità anonima
// Quorum: 2 REPRESENTATIVE + 1 SECURITY_GUARD
// ---------------------------------------------------------------------------
proposals.post('/:id/unlock-identity', requireRole('REPRESENTATIVE', 'SECURITY_GUARD'), async (c) => {
  const payload = c.get('jwtPayload');
  const proposalId = c.req.param('id');
  const body = await c.req.json<{
    rep1Id: string;
    rep2Id: string;
    securityGuardId: string;
    reason: string;
  }>();

  const { rep1Id, rep2Id, securityGuardId, reason } = body;

  if (!reason || reason.trim().length < 10) {
    return c.json({ error: 'È necessario fornire una motivazione di almeno 10 caratteri' }, 400);
  }

  // Validate that the three approvers have the correct roles
  const approvers = await c.env.DB.prepare(
    'SELECT id, role FROM users WHERE id IN (?, ?, ?)'
  ).bind(rep1Id, rep2Id, securityGuardId).all<{ id: string; role: string }>();

  const byId = Object.fromEntries(approvers.results.map((u) => [u.id, u.role]));

  if (byId[rep1Id] !== 'REPRESENTATIVE' || byId[rep2Id] !== 'REPRESENTATIVE') {
    return c.json({ error: 'I primi due ID devono appartenere a Rappresentanti' }, 400);
  }
  if (byId[securityGuardId] !== 'SECURITY_GUARD') {
    return c.json({ error: 'Il terzo ID deve appartenere a una Guardia di Sicurezza' }, 400);
  }
  if (rep1Id === rep2Id) {
    return c.json({ error: 'I due rappresentanti devono essere persone diverse' }, 400);
  }

  const proposal = await c.env.DB.prepare(
    'SELECT id, author_id, is_anonymous FROM proposals WHERE id = ?'
  ).bind(proposalId).first<{ id: string; author_id: string; is_anonymous: number }>();

  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);
  if (!proposal.is_anonymous) return c.json({ error: 'La proposta non è anonima' }, 400);

  // Check if already unlocked
  const existing = await c.env.DB.prepare(
    'SELECT id FROM anonymity_unlock_audits WHERE proposal_id = ?'
  ).bind(proposalId).first<{ id: string }>();
  if (existing) return c.json({ error: 'L\'identità è già stata sbloccata per questa proposta' }, 409);

  // Save audit record
  const auditId = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO anonymity_unlock_audits (id, proposal_id, rep_1_id, rep_2_id, security_guard_id, reason) VALUES (?, ?, ?, ?, ?, ?)'
  ).bind(auditId, proposalId, rep1Id, rep2Id, securityGuardId, reason).run();

  // Fetch author info
  const author = await c.env.DB.prepare(
    'SELECT id, first_name, last_name, username FROM users WHERE id = ?'
  ).bind(proposal.author_id).first<{ id: string; first_name: string; last_name: string; username: string }>();

  return c.json({
    success: true,
    unlockedAt: new Date().toISOString(),
    author: author ? {
      id: author.id,
      firstName: author.first_name,
      lastName: author.last_name,
      username: author.username,
    } : null,
  });
});

export default proposals;
