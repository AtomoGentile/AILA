// =============================================================================
// CIRCOLARE+ — Proposals Routes (/api/proposals/*)
// Bacheca proposte con anonimato, voti +1/-1, commenti e quorum sblocco
// =============================================================================

import { Hono } from 'hono';
import type { Context, MiddlewareHandler } from 'hono';
import type { Env, JWTPayload, ProposalStatus } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyClass, notifyUser } from '../services/fcm';

// Quorum per svelare l'autore di una proposta o di un commento anonimi (specifica v3.0, sez. 3.3).
const QUORUM_REPRESENTATIVES = 2;
const QUORUM_GUARDS = 1;

// Vero se l'utente (un solo segnaposto `?`) ha firmato uno svelamento andato a buon fine per
// quel bersaglio. L'identità è visibile solo a chi ha firmato, non a tutta la classe: una volta
// detta a voce non si richiama, quindi si tiene il cerchio il più stretto possibile.
const REVEALED_TO_VIEWER_PROPOSAL = `EXISTS (
  SELECT 1 FROM anonymity_unlock_requests r
  JOIN anonymity_unlock_approvals a ON a.request_id = r.id
  WHERE r.proposal_id = p.id AND r.comment_id IS NULL AND r.status = 'APPROVED' AND a.user_id = ?
)`;
const UNLOCK_STATUS_PROPOSAL = `(
  SELECT r.status FROM anonymity_unlock_requests r
  WHERE r.proposal_id = p.id AND r.comment_id IS NULL
  ORDER BY r.created_at DESC, r.rowid DESC LIMIT 1
)`;

type SignerRole = 'REPRESENTATIVE' | 'SECURITY_GUARD';
type ProposalsVariables = { jwtPayload: JWTPayload };

const proposals = new Hono<{ Bindings: Env; Variables: ProposalsVariables }>();

proposals.use('*', authMiddleware());

/**
 * Con quale titolo chi fa la richiesta può firmare uno svelamento: Rappresentante, oppure
 * Guardia di Sicurezza della sua classe, oppure nessuno (null).
 *
 * La Guardia non è un ruolo dell'utente ma una designazione sulla classe
 * (`classes.security_guard_id`, scelta dal Rappresentante): un compagno nominato resta STUDENT
 * per sondaggi, preferenze e valutazioni. E si legge dal database a ogni richiesta, non dal
 * token, così una nomina o una revoca ha effetto subito senza rifare il login.
 */
async function signerRole(c: Context<{ Bindings: Env; Variables: ProposalsVariables }>): Promise<SignerRole | null> {
  const payload = c.get('jwtPayload');
  const row = await c.env.DB.prepare(
    `SELECT u.role AS role, (cl.security_guard_id = u.id) AS is_guard
     FROM users u LEFT JOIN classes cl ON cl.id = u.class_id
     WHERE u.id = ?`
  ).bind(payload.sub).first<{ role: string; is_guard: number | null }>();
  if (!row) return null;
  if (row.role === 'REPRESENTATIVE') return 'REPRESENTATIVE';
  if (row.is_guard) return 'SECURITY_GUARD';
  return null;
}

const requireSigner: MiddlewareHandler<{ Bindings: Env; Variables: ProposalsVariables }> = async (c, next) => {
  const role = await signerRole(c);
  if (!role) return c.json({ error: 'Solo i Rappresentanti e la Guardia di Sicurezza possono farlo' }, 403);
  await next();
};

// ---------------------------------------------------------------------------
// GET /api/proposals — Lista proposte (filtrate per status)
// ---------------------------------------------------------------------------
proposals.get('/', async (c) => {
  const status = c.req.query('status') as ProposalStatus | undefined;
  const payload = c.get('jwtPayload');
  const canModerateIdentity = (await signerRole(c)) !== null;

  let query = `
    SELECT p.id, p.is_anonymous, p.title, p.description, p.category, p.status, p.outcome,
           p.modified_by_rep, p.edited_at, p.created_at, p.author_id,
           u.first_name as author_first_name, u.last_name as author_last_name,
           ${REVEALED_TO_VIEWER_PROPOSAL} as revealed_to_me,
           ${UNLOCK_STATUS_PROPOSAL} as unlock_status,
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
  // L'ordine segue i segnaposto nel testo: prima quello di revealed_to_me (nella SELECT), poi il
  // join sul mio voto, infine la classe.
  const params: (string | number)[] = [payload.sub, payload.sub, await resolveClassId(c)];

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
    outcome: string | null;
    modified_by_rep: number;
    edited_at: string | null;
    created_at: string;
    author_id: string;
    author_first_name: string;
    author_last_name: string;
    revealed_to_me: number;
    unlock_status: string | null;
    up_votes: number;
    down_votes: number;
    comment_count: number;
    my_vote: number | null;
  }>();

  return c.json({
    proposals: rows.results.map((p) => {
      // L'autore (id + nome) lo vede chi non è anonimo, l'autore stesso e chi ha firmato uno
      // svelamento andato a buon fine. Prima lo vedeva sempre anche il Rappresentante da solo,
      // e questo rendeva inutile il quorum: ora servono tutte e tre le approvazioni.
      const isMine = p.author_id === payload.sub;
      const canSeeAuthor = !p.is_anonymous || isMine || Boolean(p.revealed_to_me);
      return {
        id: p.id,
        title: p.title,
        description: p.description,
        category: p.category,
        status: p.status,
        outcome: p.outcome,
        isAnonymous: Boolean(p.is_anonymous),
        authorId: canSeeAuthor ? p.author_id : null,
        authorName: canSeeAuthor ? `${p.author_first_name} ${p.author_last_name}` : null,
        // Vero quando l'autore è visibile solo perché lo svelamento è stato approvato.
        identityRevealed: Boolean(p.is_anonymous) && !isMine && Boolean(p.revealed_to_me),
        // Stato dell'ultima richiesta di svelamento (solo per chi può gestirle).
        unlockRequestStatus: p.is_anonymous && canModerateIdentity ? p.unlock_status : null,
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
// PUT /api/proposals/security-guard — Il Rappresentante sceglie (o revoca) la Guardia
// ---------------------------------------------------------------------------
//
// La terza firma del quorum non può essere un Rappresentante (servono tre persone diverse) e
// non se la sceglie da sola: la nomina il Rappresentante fra i compagni della classe, dalla
// Scheda Classe. Una sola per classe: nominarne una nuova sostituisce la precedente.
// Va registrata PRIMA di `PUT /:id`: Hono prende la prima rotta che combacia, e "security-guard"
// verrebbe letto come un id di proposta.
proposals.put('/security-guard', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const { userId = null } = await c.req.json<{ userId?: string | null }>();

  if (userId) {
    const target = await c.env.DB.prepare('SELECT id, role FROM users WHERE id = ? AND class_id = ?')
      .bind(userId, classId).first<{ id: string; role: string }>();
    if (!target) return c.json({ error: 'Compagno non trovato nella tua classe' }, 404);
    if (target.role === 'REPRESENTATIVE') {
      return c.json({ error: 'La Guardia deve essere una persona diversa dai due Rappresentanti' }, 400);
    }
  }

  const previous = await c.env.DB.prepare('SELECT security_guard_id FROM classes WHERE id = ?')
    .bind(classId).first<{ security_guard_id: string | null }>();

  await c.env.DB.batch([
    c.env.DB.prepare('UPDATE classes SET security_guard_id = ? WHERE id = ?').bind(userId, classId),
    // Le firme da Guardia date sulle richieste ancora aperte valevano per la persona di prima:
    // con una Guardia nuova (o nessuna) non devono più contare.
    c.env.DB.prepare(
      `DELETE FROM anonymity_unlock_approvals
       WHERE role = 'SECURITY_GUARD' AND request_id IN (
         SELECT id FROM anonymity_unlock_requests WHERE class_id = ? AND status = 'PENDING'
       )`
    ).bind(classId),
  ]);

  if (userId && userId !== previous?.security_guard_id) {
    await notifyUser(
      c.env,
      userId,
      'Sei la Guardia di Sicurezza della classe',
      'Con i due Rappresentanti approvi lo svelamento di proposte e commenti anonimi.',
      { action: 'security_guard_assigned' }
    );
  }

  return c.json({ success: true, securityGuardId: userId });
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
//
// Il ciclo è: NUOVA → (IN_ANALISI, solo per le proposte più complesse) → CHIUSA con un esito,
// ACCETTATA o RIFIUTATA. Il Rappresentante può chiudere direttamente una NUOVA senza passare
// dall'analisi, e riaprire una chiusa per correggere un errore.
proposals.put('/:id/status', requireRole('REPRESENTATIVE'), async (c) => {
  const id = c.req.param('id');
  const { status, outcome } = await c.req.json<{ status: ProposalStatus; outcome?: string | null }>();

  const validStatuses: ProposalStatus[] = ['NUOVA', 'IN_ANALISI', 'CHIUSA'];
  if (!validStatuses.includes(status)) {
    return c.json({ error: `Status non valido. Valori ammessi: ${validStatuses.join(', ')}` }, 400);
  }
  if (status === 'CHIUSA' && outcome !== 'ACCETTATA' && outcome !== 'RIFIUTATA') {
    return c.json({ error: "Per chiudere una proposta indica l'esito: ACCETTATA o RIFIUTATA" }, 400);
  }

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  // L'esito ha senso solo per le chiuse: riaprendo una proposta si azzera.
  const finalOutcome = status === 'CHIUSA' ? outcome : null;
  await c.env.DB.prepare('UPDATE proposals SET status = ?, outcome = ? WHERE id = ?')
    .bind(status, finalOutcome, id).run();

  return c.json({ success: true, status, outcome: finalOutcome });
});

// ---------------------------------------------------------------------------
// DELETE /api/proposals/:id — Elimina proposta (autore o REPRESENTATIVE)
// ---------------------------------------------------------------------------
proposals.delete('/:id', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');

  // AND class_id: senza, un rappresentante di un'altra classe poteva cancellare le proposte altrui.
  const proposal = await c.env.DB.prepare('SELECT id, author_id FROM proposals WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string; author_id: string }>();
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

  const proposal = await c.env.DB.prepare('SELECT id, status FROM proposals WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string; status: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  // Una proposta chiusa (accettata o rifiutata) ha già una decisione: non si vota più. Il server
  // lo impone oltre all'app, così nessun client vecchio o modificato può cambiare i numeri.
  if (proposal.status === 'CHIUSA') {
    return c.json({ error: 'La proposta è chiusa: non si può più votare' }, 409);
  }

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
  const canModerateIdentity = (await signerRole(c)) !== null;

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const rows = await c.env.DB.prepare(
    `SELECT c.id, c.user_id, c.content, c.created_at, c.is_anonymous,
            u.first_name, u.last_name,
            EXISTS (
              SELECT 1 FROM anonymity_unlock_requests r
              JOIN anonymity_unlock_approvals a ON a.request_id = r.id
              WHERE r.comment_id = c.id AND r.status = 'APPROVED' AND a.user_id = ?
            ) as revealed_to_me,
            (
              SELECT r.status FROM anonymity_unlock_requests r
              WHERE r.comment_id = c.id
              ORDER BY r.created_at DESC, r.rowid DESC LIMIT 1
            ) as unlock_status
     FROM proposal_comments c
     JOIN users u ON u.id = c.user_id
     WHERE c.proposal_id = ?
     ORDER BY c.created_at ASC`
  ).bind(payload.sub, id).all<{
    id: string;
    user_id: string;
    content: string;
    created_at: string;
    is_anonymous: number;
    first_name: string;
    last_name: string;
    revealed_to_me: number;
    unlock_status: string | null;
  }>();

  return c.json({
    comments: rows.results.map((cmt) => {
      const isMine = cmt.user_id === payload.sub;
      const anonymous = Boolean(cmt.is_anonymous);
      const canSeeAuthor = !anonymous || isMine || Boolean(cmt.revealed_to_me);
      return {
        id: cmt.id,
        // userId e nome restano nascosti per un commento anonimo: nome e id viaggiavano sempre
        // nella risposta, quindi l'anonimato sarebbe stato solo grafico.
        userId: canSeeAuthor ? cmt.user_id : '',
        authorName: canSeeAuthor ? `${cmt.first_name} ${cmt.last_name}` : 'Anonimo',
        content: cmt.content,
        createdAt: cmt.created_at,
        isAnonymous: anonymous,
        isMine,
        identityRevealed: anonymous && !isMine && Boolean(cmt.revealed_to_me),
        unlockRequestStatus: anonymous && canModerateIdentity ? cmt.unlock_status : null,
      };
    }),
  });
});

// ---------------------------------------------------------------------------
// POST /api/proposals/:id/comments — Aggiungi commento (anche anonimo)
// ---------------------------------------------------------------------------
proposals.post('/:id/comments', async (c) => {
  const payload = c.get('jwtPayload');
  const id = c.req.param('id');
  const { content, isAnonymous = false } = await c.req.json<{ content: string; isAnonymous?: boolean }>();

  if (!content || content.trim().length === 0) {
    return c.json({ error: 'Il commento non può essere vuoto' }, 400);
  }
  if (content.length > 1000) {
    return c.json({ error: 'Il commento non può superare 1000 caratteri' }, 400);
  }

  const proposal = await c.env.DB.prepare('SELECT id FROM proposals WHERE id = ? AND class_id = ?')
    .bind(id, await resolveClassId(c)).first<{ id: string }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  const commentId = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO proposal_comments (id, proposal_id, user_id, content, is_anonymous) VALUES (?, ?, ?, ?, ?)'
  ).bind(commentId, id, payload.sub, content.trim(), isAnonymous ? 1 : 0).run();

  return c.json({ success: true, id: commentId }, 201);
});

// =============================================================================
// SVELAMENTO DELL'AUTORE ANONIMO — quorum 2 Rappresentanti + 1 Guardia di Sicurezza
// =============================================================================
//
// Prima esisteva un solo endpoint in cui una persona scriveva gli id di tutti e tre i firmatari:
// nessuno di loro doveva dire di sì, quindi il quorum era solo di facciata. Ora è un processo:
//   1. un Rappresentante o la Guardia apre una richiesta con la motivazione (conta già come
//      la sua approvazione);
//   2. gli altri approvano ciascuno dal proprio account;
//   3. a 2 Rappresentanti + 1 Guardia la richiesta passa ad APPROVED e i tre firmatari vedono
//      l'autore. Chiunque dei tre può invece rifiutarla, e la richiesta si chiude.
// Vale per l'autore di una proposta e per quello di un commento.

// ---------------------------------------------------------------------------
// GET /api/proposals/unlock-requests — Richieste in attesa
// ---------------------------------------------------------------------------
//
// Aperta a tutti: agli altri risponde `canSign: false` e nessuna richiesta, così l'app capisce
// se mostrare il pannello senza dover conoscere chi è la Guardia.
proposals.get('/unlock-requests', async (c) => {
  const payload = c.get('jwtPayload');
  const classId = await resolveClassId(c);
  if (!(await signerRole(c))) return c.json({ canSign: false, requests: [] });

  const requests = await c.env.DB.prepare(
    `SELECT r.id, r.proposal_id, r.comment_id, r.reason, r.created_at,
            p.title as proposal_title, cm.content as comment_content,
            ru.first_name as requester_first_name, ru.last_name as requester_last_name
     FROM anonymity_unlock_requests r
     JOIN proposals p ON p.id = r.proposal_id
     JOIN users ru ON ru.id = r.requested_by
     LEFT JOIN proposal_comments cm ON cm.id = r.comment_id
     WHERE r.class_id = ? AND r.status = 'PENDING'
     ORDER BY r.created_at DESC`
  ).bind(classId).all<{
    id: string;
    proposal_id: string;
    comment_id: string | null;
    reason: string;
    created_at: string;
    proposal_title: string;
    comment_content: string | null;
    requester_first_name: string;
    requester_last_name: string;
  }>();

  const approvals = await c.env.DB.prepare(
    `SELECT a.request_id, a.user_id, a.role, u.first_name, u.last_name
     FROM anonymity_unlock_approvals a
     JOIN users u ON u.id = a.user_id
     WHERE a.request_id IN (
       SELECT id FROM anonymity_unlock_requests WHERE class_id = ? AND status = 'PENDING'
     )`
  ).bind(classId).all<{ request_id: string; user_id: string; role: string; first_name: string; last_name: string }>();

  return c.json({
    canSign: true,
    requests: requests.results.map((r) => {
      const signed = approvals.results.filter((a) => a.request_id === r.id);
      return {
        id: r.id,
        proposalId: r.proposal_id,
        proposalTitle: r.proposal_title,
        commentId: r.comment_id,
        commentExcerpt: r.comment_content ? r.comment_content.slice(0, 140) : null,
        reason: r.reason,
        requestedByName: `${r.requester_first_name} ${r.requester_last_name}`,
        createdAt: r.created_at,
        representativeApprovals: signed.filter((a) => a.role === 'REPRESENTATIVE').length,
        guardApprovals: signed.filter((a) => a.role === 'SECURITY_GUARD').length,
        representativesNeeded: QUORUM_REPRESENTATIVES,
        guardsNeeded: QUORUM_GUARDS,
        approvedByMe: signed.some((a) => a.user_id === payload.sub),
        approverNames: signed.map((a) => `${a.first_name} ${a.last_name}`),
      };
    }),
  });
});

// ---------------------------------------------------------------------------
// POST /api/proposals/:id/unlock-requests — Apri una richiesta di svelamento
// ---------------------------------------------------------------------------
proposals.post('/:id/unlock-requests', requireSigner, async (c) => {
  const payload = c.get('jwtPayload');
  const proposalId = c.req.param('id');
  const classId = await resolveClassId(c);
  const { commentId = null, reason } = await c.req.json<{ commentId?: string | null; reason: string }>();

  if (!reason || reason.trim().length < 10) {
    return c.json({ error: 'È necessario fornire una motivazione di almeno 10 caratteri' }, 400);
  }

  const proposal = await c.env.DB.prepare(
    'SELECT id, title, is_anonymous FROM proposals WHERE id = ? AND class_id = ?'
  ).bind(proposalId, classId).first<{ id: string; title: string; is_anonymous: number }>();
  if (!proposal) return c.json({ error: 'Proposta non trovata' }, 404);

  if (commentId) {
    const comment = await c.env.DB.prepare(
      'SELECT id, is_anonymous FROM proposal_comments WHERE id = ? AND proposal_id = ?'
    ).bind(commentId, proposalId).first<{ id: string; is_anonymous: number }>();
    if (!comment) return c.json({ error: 'Commento non trovato' }, 404);
    if (!comment.is_anonymous) return c.json({ error: 'Il commento non è anonimo' }, 400);
  } else if (!proposal.is_anonymous) {
    return c.json({ error: 'La proposta non è anonima' }, 400);
  }

  // Una sola richiesta viva per bersaglio: una in corso, o già approvata. Una rifiutata si può
  // riproporre (con una motivazione migliore, per esempio).
  const existing = await c.env.DB.prepare(
    `SELECT id, status FROM anonymity_unlock_requests
     WHERE proposal_id = ? AND comment_id IS ? AND status IN ('PENDING', 'APPROVED')`
  ).bind(proposalId, commentId).first<{ id: string; status: string }>();
  if (existing) {
    return c.json(
      { error: existing.status === 'APPROVED' ? "L'identità è già stata svelata" : 'Esiste già una richiesta in corso' },
      409
    );
  }

  const requesterRole = (await signerRole(c)) as SignerRole;

  // Senza Guardia il quorum non può chiudersi mai: meglio dirlo subito che lasciare una richiesta
  // che aspetta per sempre una firma che nessuno può dare.
  const guardRow = await c.env.DB.prepare('SELECT security_guard_id FROM classes WHERE id = ?')
    .bind(classId).first<{ security_guard_id: string | null }>();
  if (!guardRow?.security_guard_id) {
    return c.json(
      { error: 'La classe non ha ancora una Guardia di Sicurezza: sceglila dalla Scheda Classe' },
      409
    );
  }

  const requestId = newUUID();
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO anonymity_unlock_requests (id, class_id, proposal_id, comment_id, requested_by, reason)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(requestId, classId, proposalId, commentId, payload.sub, reason.trim()),
    c.env.DB.prepare(
      'INSERT INTO anonymity_unlock_approvals (request_id, user_id, role) VALUES (?, ?, ?)'
    ).bind(requestId, payload.sub, requesterRole),
  ]);

  // Avvisa gli altri firmatari possibili. Il testo non dice chi ha scritto cosa (nessuno lo sa
  // ancora), solo di quale proposta si tratta.
  const others = await c.env.DB.prepare(
    `SELECT u.id FROM users u JOIN classes cl ON cl.id = u.class_id
     WHERE u.class_id = ? AND u.id != ? AND (u.role = 'REPRESENTATIVE' OR u.id = cl.security_guard_id)`
  ).bind(classId, payload.sub).all<{ id: string }>();
  await Promise.allSettled(
    others.results.map((u) =>
      notifyUser(
        c.env,
        u.id,
        'Richiesta di svelamento anonimato',
        `Serve la tua approvazione: "${proposal.title}"`,
        { action: 'unlock_request', proposal_id: proposal.id }
      )
    )
  );

  return c.json({ success: true, id: requestId }, 201);
});

// ---------------------------------------------------------------------------
// POST /api/proposals/unlock-requests/:requestId/approve — Approva
// ---------------------------------------------------------------------------
proposals.post('/unlock-requests/:requestId/approve', requireSigner, async (c) => {
  const payload = c.get('jwtPayload');
  const requestId = c.req.param('requestId');
  const classId = await resolveClassId(c);

  const request = await c.env.DB.prepare(
    'SELECT id, proposal_id, reason, status FROM anonymity_unlock_requests WHERE id = ? AND class_id = ?'
  ).bind(requestId, classId).first<{ id: string; proposal_id: string; reason: string; status: string }>();
  if (!request) return c.json({ error: 'Richiesta non trovata' }, 404);
  if (request.status !== 'PENDING') return c.json({ error: 'La richiesta non è più in corso' }, 409);

  const role = (await signerRole(c)) as SignerRole;

  const inserted = await c.env.DB.prepare(
    'INSERT OR IGNORE INTO anonymity_unlock_approvals (request_id, user_id, role) VALUES (?, ?, ?)'
  ).bind(requestId, payload.sub, role).run();
  if (!inserted.meta.changes) return c.json({ error: 'Hai già approvato questa richiesta' }, 409);

  const all = await c.env.DB.prepare(
    'SELECT user_id, role FROM anonymity_unlock_approvals WHERE request_id = ? ORDER BY approved_at, rowid'
  ).bind(requestId).all<{ user_id: string; role: string }>();
  const reps = all.results.filter((a) => a.role === 'REPRESENTATIVE');
  const guards = all.results.filter((a) => a.role === 'SECURITY_GUARD');

  const quorumReached = reps.length >= QUORUM_REPRESENTATIVES && guards.length >= QUORUM_GUARDS;
  if (quorumReached) {
    // Il WHERE status = 'PENDING' evita che due approvazioni arrivate insieme chiudano (e
    // registrino nell'audit) la stessa richiesta due volte.
    const closed = await c.env.DB.prepare(
      `UPDATE anonymity_unlock_requests SET status = 'APPROVED', resolved_at = CURRENT_TIMESTAMP
       WHERE id = ? AND status = 'PENDING'`
    ).bind(requestId).run();
    if (closed.meta.changes) {
      await c.env.DB.prepare(
        `INSERT INTO anonymity_unlock_audits (id, proposal_id, rep_1_id, rep_2_id, security_guard_id, reason)
         VALUES (?, ?, ?, ?, ?, ?)`
      ).bind(newUUID(), request.proposal_id, reps[0].user_id, reps[1].user_id, guards[0].user_id, request.reason).run();
    }
  }

  return c.json({
    success: true,
    status: quorumReached ? 'APPROVED' : 'PENDING',
    representativeApprovals: reps.length,
    guardApprovals: guards.length,
  });
});

// ---------------------------------------------------------------------------
// POST /api/proposals/unlock-requests/:requestId/reject — Rifiuta (veto di uno dei tre)
// ---------------------------------------------------------------------------
proposals.post('/unlock-requests/:requestId/reject', requireSigner, async (c) => {
  const requestId = c.req.param('requestId');

  const closed = await c.env.DB.prepare(
    `UPDATE anonymity_unlock_requests SET status = 'REJECTED', resolved_at = CURRENT_TIMESTAMP
     WHERE id = ? AND class_id = ? AND status = 'PENDING'`
  ).bind(requestId, await resolveClassId(c)).run();
  if (!closed.meta.changes) return c.json({ error: 'Richiesta non trovata o già chiusa' }, 404);

  return c.json({ success: true });
});

export default proposals;
