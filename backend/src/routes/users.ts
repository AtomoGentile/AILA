// =============================================================================
// CIRCOLARE+ — Users Routes (rotte /api/users)
// =============================================================================

import { Hono } from 'hono';
import type { Context } from 'hono';
import type { Env, JWTPayload } from '../types';
import {
  authMiddleware,
  issueToken,
  newUUID,
  passwordProblem,
  randomCode,
  requireRole,
  resolveClassId,
  sha256Hex,
  storedPasswordHash,
  verifyPassword,
} from '../auth';
import { classInviteCode, regenerateClassInviteCode } from '../services/classInvites';
import { notifyUser, notifyUsers } from '../services/fcm';
import { inBackground } from '../services/background';
import { fullName, nameCase } from '../names';
import type { UserRole } from '../types';

const users = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

users.use('*', authMiddleware());

// Come in auth.ts: grafia e lunghezza massima di nome e cognome restano allineate.
const MAX_NAME_LENGTH = 40;

// ---------------------------------------------------------------------------
// GET /api/users/me — Profilo proprio
// ---------------------------------------------------------------------------
users.get('/me', async (c) => {
  const payload = c.get('jwtPayload');

  const user = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, u.username, u.role, u.created_at,
            u.class_id, cl.label AS class_label,
            sp.height_cm, sp.priority_pass, sp.notification_board_enabled
     FROM users u
     LEFT JOIN classes cl ON cl.id = u.class_id
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.id = ?`
  ).bind(payload.sub).first<{
    id: string;
    first_name: string;
    last_name: string;
    username: string;
    role: string;
    created_at: string;
    class_id: string | null;
    class_label: string | null;
    height_cm: number | null;
    priority_pass: number | null;
    notification_board_enabled: number | null;
  }>();

  if (!user) return c.json({ error: 'Utente non trovato' }, 404);

  return c.json({
    id: user.id,
    firstName: nameCase(user.first_name),
    lastName: nameCase(user.last_name),
    username: user.username,
    role: user.role,
    createdAt: user.created_at,
    classId: user.class_id ?? 'DEFAULT_CLASS',
    classLabel: user.class_label,
    heightCm: user.height_cm,
    priorityPass: Boolean(user.priority_pass),
    notificationBoardEnabled: Boolean(user.notification_board_enabled ?? 1),
  });
});

// ---------------------------------------------------------------------------
// PUT /api/users/me/notifications — Toggle notifiche bacheca
// ---------------------------------------------------------------------------
users.put('/me/notifications', async (c) => {
  const payload = c.get('jwtPayload');
  const { boardEnabled } = await c.req.json<{ boardEnabled: boolean }>();

  await c.env.DB.prepare(
    'UPDATE student_profiles SET notification_board_enabled = ? WHERE user_id = ?'
  ).bind(boardEnabled ? 1 : 0, payload.sub).run();

  return c.json({ success: true, notificationBoardEnabled: boardEnabled });
});

// ---------------------------------------------------------------------------
// PUT /api/users/me/password — Cambia la propria password
//
// Risponde con un token nuovo: quelli emessi prima (altri telefoni, un token rubato) smettono
// di valere, perche' la "versione" della password nel token non corrisponde piu'.
// ---------------------------------------------------------------------------
users.put('/me/password', async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{ currentPassword?: string; newPassword?: string }>();
  if (typeof body.currentPassword !== 'string' || !body.currentPassword) {
    return c.json({ error: 'Scrivi la password attuale' }, 400);
  }
  const weakPassword = passwordProblem(body.newPassword);
  if (weakPassword) return c.json({ error: weakPassword }, 400);

  const user = await c.env.DB.prepare('SELECT id, username, role, class_id, password_hash FROM users WHERE id = ?')
    .bind(payload.sub)
    .first<{ id: string; username: string; role: UserRole; class_id: string | null; password_hash: string }>();
  if (!user) return c.json({ error: 'Utente non trovato' }, 404);

  const [storedHash, salt] = user.password_hash.split(':');
  if (!(await verifyPassword(body.currentPassword, storedHash, salt))) {
    return c.json({ error: 'La password attuale non è corretta' }, 403);
  }

  const passwordHash = await storedPasswordHash(body.newPassword as string);
  await c.env.DB.prepare('UPDATE users SET password_hash = ? WHERE id = ?').bind(passwordHash, user.id).run();

  const token = await issueToken(c.env, {
    id: user.id,
    username: user.username,
    role: user.role,
    classId: user.class_id || 'DEFAULT_CLASS',
    passwordHash,
  });
  return c.json({ success: true, token });
});

// ---------------------------------------------------------------------------
// GET /api/users/class-code — Codice per entrare nella classe (solo REPRESENTATIVE)
// POST /api/users/class-code/regenerate — Nuovo codice (il vecchio smette di valere)
// ---------------------------------------------------------------------------
users.get('/class-code', requireRole('REPRESENTATIVE'), async (c) => {
  const code = await classInviteCode(c.env, await resolveClassId(c), { createIfMissing: true });
  if (!code) return c.json({ error: 'Codici classe non ancora attivi sul server (manca la migrazione 013)' }, 503);
  return c.json({ code });
});

users.post('/class-code/regenerate', requireRole('REPRESENTATIVE'), async (c) => {
  const code = await regenerateClassInviteCode(c.env, await resolveClassId(c));
  if (!code) return c.json({ error: 'Codici classe non ancora attivi sul server (manca la migrazione 013)' }, 503);
  return c.json({ code });
});

// ---------------------------------------------------------------------------
// POST /api/users/:id/reset-code — Codice monouso per reimpostare la password di un compagno
// (solo REPRESENTATIVE della stessa classe). Vale 24 ore; generarne un altro annulla il primo.
// ---------------------------------------------------------------------------
const RESET_CODE_TTL_SECONDS = 24 * 60 * 60;

users.post('/:id/reset-code', requireRole('REPRESENTATIVE'), async (c) => {
  const payload = c.get('jwtPayload');
  const targetId = c.req.param('id');
  if (targetId === payload.sub) {
    return c.json({ error: 'Per la tua password usa "Cambia password" nel profilo' }, 400);
  }
  const target = await c.env.DB.prepare('SELECT id, username, role FROM users WHERE id = ? AND class_id = ?')
    .bind(targetId, await resolveClassId(c))
    .first<{ id: string; username: string; role: UserRole }>();
  if (!target) return c.json({ error: 'Compagno non trovato nella tua classe' }, 404);
  // Non sull'altro Rappresentante: entrando nel suo account una persona sola avrebbe le due firme
  // da Rappresentante del quorum (la Guardia la nomina lei stessa) e svelerebbe gli anonimi da sola.
  if (target.role === 'REPRESENTATIVE') {
    return c.json({ error: "Non puoi reimpostare la password dell'altro Rappresentante" }, 403);
  }

  const code = randomCode(8);
  const expiresAt = Math.floor(Date.now() / 1000) + RESET_CODE_TTL_SECONDS;
  try {
    await c.env.DB.prepare(
      `INSERT INTO password_reset_codes (user_id, code_hash, expires_at, created_by, created_at)
       VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
       ON CONFLICT(user_id) DO UPDATE SET code_hash = excluded.code_hash, expires_at = excluded.expires_at,
         created_by = excluded.created_by, created_at = excluded.created_at`
    ).bind(target.id, await sha256Hex(code), expiresAt, payload.sub).run();
  } catch {
    return c.json({ error: 'Reset password non ancora attivo sul server (manca la migrazione 013)' }, 503);
  }

  // Avvisa il compagno: se non l'ha chiesto lui, lo sa prima che qualcuno usi il codice al suo posto.
  inBackground(c, notifyUser(
    c.env,
    target.id,
    'Codice per reimpostare la password',
    'Il Rappresentante ha generato un codice per cambiare la tua password. Se non l\'hai chiesto tu, parlane subito con lui o con la Guardia.',
    { action: 'password_reset_code' }
  ));

  return c.json({ code, username: target.username, expiresAt: new Date(expiresAt * 1000).toISOString() });
});

// ---------------------------------------------------------------------------
// PUT /api/users/:id/height — Il Rappresentante corregge l'altezza di un compagno
//
// L'altezza decide chi sta davanti nella Mappa Posti: dichiararla più bassa del vero sposta
// il banco, e il posto non è più giusto per gli altri. Il Rappresentante può correggerla, ma
// non in silenzio: chi la subisce riceve subito una notifica con il vecchio e il nuovo valore,
// così se la correzione è sbagliata può parlarne. Nessuna modifica senza avviso, quindi.
// ---------------------------------------------------------------------------
users.put('/:id/height', requireRole('REPRESENTATIVE'), async (c) => {
  const payload = c.get('jwtPayload');
  const targetId = c.req.param('id');

  let heightCm: unknown;
  try {
    heightCm = (await c.req.json<{ heightCm?: unknown }>()).heightCm;
  } catch {
    return c.json({ error: 'Corpo della richiesta non valido' }, 400);
  }
  if (
    typeof heightCm !== 'number' || !Number.isInteger(heightCm) ||
    heightCm < 140 || heightCm > 210 || heightCm % 5 !== 0
  ) {
    return c.json({ error: "L'altezza deve essere tra 140 e 210 cm a scaglioni di 5 cm" }, 400);
  }

  // Stessa classe, e solo studenti o Rappresentanti: un Rappresentante non tocca le altre classi.
  const target = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, sp.height_cm
     FROM users u
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.id = ? AND u.class_id = ? AND u.role IN ('STUDENT', 'REPRESENTATIVE')`
  ).bind(targetId, await resolveClassId(c)).first<{
    id: string;
    first_name: string;
    height_cm: number | null;
  }>();
  if (!target) return c.json({ error: 'Compagno non trovato nella tua classe' }, 404);

  const previous = target.height_cm;
  if (previous === heightCm) return c.json({ success: true, heightCm, unchanged: true });

  await c.env.DB.prepare(
    `INSERT INTO student_profiles (user_id, height_cm) VALUES (?, ?)
     ON CONFLICT(user_id) DO UPDATE SET height_cm = excluded.height_cm`
  ).bind(targetId, heightCm).run();

  // Se il Rappresentante corregge la propria altezza non c'è nessuno da avvisare.
  if (target.id !== payload.sub) {
    // Il nome di chi ha corretto: la notifica dice da chi andare a chiedere conto, non un anonimo
    // "il Rappresentante" che in classe sono in due.
    const author = await c.env.DB.prepare('SELECT first_name, last_name FROM users WHERE id = ?')
      .bind(payload.sub)
      .first<{ first_name: string; last_name: string }>();
    const authorName = author ? fullName(author.first_name, author.last_name) : 'Il Rappresentante';
    inBackground(c, notifyUser(
      c.env,
      target.id,
      'Altezza aggiornata',
      previous == null
        ? `${authorName} ha impostato la tua altezza a ${heightCm} cm. Se non è giusto, parlane con chi l'ha corretta.`
        : `${authorName} ha corretto la tua altezza da ${previous} a ${heightCm} cm. ` +
          `Se non è giusto, parlane con chi l'ha corretta.`,
      { action: 'height_updated' }
    ));
  }

  return c.json({ success: true, heightCm, previousHeightCm: previous });
});

// CAMBIO NOME — le richieste, da approvare al Rappresentante
//
// Un nome sbagliato si scrive in fretta e prima restava per sempre. Ora funziona come i
// documenti si immaginano: lo studente chiede (POST /api/users/me/name-request), il
// Rappresentante — o la Guardia di Sicurezza — approva o rifiuta; la richiesta avvisa i
// firmatari della classe, la decisione avvisa il richiedente. Nessuno decide sulla propria
// richiesta: un Rappresentante che cambiasse nome da solo avrebbe un nome che l'altra firma
// non ha mai visto (e l'elenco degli anonimi da svelare lo leggerebbe con i nomi vecchi).
// ---------------------------------------------------------------------------

/** Chi può approvare nella classe: i Rappresentanti e la Guardia, tranne chi ha chiesto. */
async function nameRequestApprovers(env: Env, classId: string, excludeUserId: string): Promise<string[]> {
  const rows = await env.DB.prepare(
    `SELECT u.id FROM users u JOIN classes cl ON cl.id = u.class_id
     WHERE u.class_id = ? AND u.id != ? AND (u.role = 'REPRESENTATIVE' OR u.id = cl.security_guard_id)`
  ).bind(classId, excludeUserId).all<{ id: string }>();
  return rows.results.map((r) => r.id);
}

users.post('/me/name-request', async (c) => {
  const payload = c.get('jwtPayload');
  const body = await c.req.json<{ firstName?: string; lastName?: string }>();
  // Grafia unica anche qui: la richiesta che il Rappresentante legge e il nome approvato
  // vengono salvati come compariranno dappertutto.
  const firstName = typeof body.firstName === 'string' ? nameCase(body.firstName) : '';
  const lastName = typeof body.lastName === 'string' ? nameCase(body.lastName) : '';
  if (!firstName || !lastName) {
    return c.json({ error: 'Nome e cognome sono obbligatori' }, 400);
  }
  if (firstName.length > MAX_NAME_LENGTH || lastName.length > MAX_NAME_LENGTH) {
    return c.json({ error: `Nome e cognome possono avere al massimo ${MAX_NAME_LENGTH} caratteri` }, 400);
  }

  const user = await c.env.DB.prepare('SELECT id, first_name, last_name, class_id FROM users WHERE id = ?')
    .bind(payload.sub)
    .first<{ id: string; first_name: string; last_name: string; class_id: string | null }>();
  if (!user) return c.json({ error: 'Utente non trovato' }, 404);

  // SoloDifferente davvero: la stessa cosa scritta con maiuscole diverse non merita una firma.
  if (nameCase(user.first_name) === firstName && nameCase(user.last_name) === lastName) {
    return c.json({ error: 'Il nome è già quello' }, 400);
  }

  const requestId = newUUID();
  try {
    await c.env.DB.batch([
      // Una richiesta sola in sospeso per persona: la nuova sostituisce la precedente
      // (un "avevo mancato una lettera, riscrivo" non lascia richieste doppie in lista).
      c.env.DB.prepare(
        "DELETE FROM name_change_requests WHERE user_id = ? AND status = 'PENDING'"
      ).bind(user.id),
      c.env.DB.prepare(
        `INSERT INTO name_change_requests
           (id, user_id, old_first_name, old_last_name, new_first_name, new_last_name, status, created_at)
         VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)`
      ).bind(requestId, user.id, user.first_name, user.last_name, firstName, lastName),
    ]);
  } catch {
    return c.json({ error: 'Cambio nome non ancora attivo sul server (manca la migrazione 020)' }, 503);
  }

  // Se è il richiedente stesso un Rappresentante, la lista resta vuota: la decisione spetta
  // all'altro Rappresentante o alla Guardia e la richiesta resta in attesa dal suo profilo.
  inBackground(c, notifyUsers(
    c.env,
    await nameRequestApprovers(c.env, user.class_id || 'DEFAULT_CLASS', user.id),
    'Richiesta di cambio nome',
    `${nameCase(user.first_name)} ${nameCase(user.last_name)} chiede di chiamarsi ${firstName} ${lastName}. Lo trova nel Profilo, sezione Richieste di cambio nome.`,
    { action: 'name_change_request', user_id: user.id }
  ));

  return c.json({ success: true, status: 'PENDING' }, 201);
});

// La propria richiesta in attesa, oppure `null`: il dialogo del Profilo la mostra così chi ha
// chiesto sa di non aver avuto risposta ancora.
users.get('/me/name-request', async (c) => {
  const payload = c.get('jwtPayload');
  const row = await c.env.DB.prepare(
    `SELECT id, new_first_name, new_last_name, created_at
     FROM name_change_requests WHERE user_id = ? AND status = 'PENDING'
     ORDER BY created_at DESC, rowid DESC LIMIT 1`
  ).bind(payload.sub).first<{ id: string; new_first_name: string; new_last_name: string; created_at: string }>()
    .catch(() => null);

  return c.json({
    request: row ? { id: row.id, firstName: row.new_first_name, lastName: row.new_last_name, createdAt: row.created_at } : null,
  });
});

// Elenco delle richieste in attesa nella propria classe, per chi decide.
users.get('/name-requests/pending', requireRole('REPRESENTATIVE', 'SECURITY_GUARD'), async (c) => {
  const payload = c.get('jwtPayload');
  const classId = await resolveClassId(c);

  const rows = await c.env.DB.prepare(
    `SELECT r.id, r.user_id, r.old_first_name, r.old_last_name, r.new_first_name, r.new_last_name,
            r.created_at, u.username AS requester_username,
            (SELECT cl.label FROM classes cl WHERE cl.id = u.class_id) AS requester_class_label
     FROM name_change_requests r
     JOIN users u ON u.id = r.user_id
     WHERE u.class_id = ? AND r.status = 'PENDING'
     ORDER BY r.created_at ASC`
  ).bind(classId).all<{
    id: string;
    user_id: string;
    old_first_name: string;
    old_last_name: string;
    new_first_name: string;
    new_last_name: string;
    created_at: string;
    requester_class_label: string | null;
  }>().catch(() => null);

  return c.json({
    requests: (rows?.results ?? []).map((r) => ({
      id: r.id,
      userId: r.user_id,
      oldName: fullName(r.old_first_name, r.old_last_name),
      newFirstName: nameCase(r.new_first_name),
      newLastName: nameCase(r.new_last_name),
      createdAt: r.created_at,
      // Su sé stessi non si può decidere.
      canDecide: r.user_id !== payload.sub,
    })),
  });
});

/** Approva o rifiuta: aggiorna la richiesta, il nome (se approvata) e avvisa il richiedente. */
function nameRequestDecision(decision: 'APPROVED' | 'REJECTED') {
  return async (c: Context<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>) => {
    const payload = c.get('jwtPayload');
    // Solo Rappresentanti e Guardie decidono (sé stessi esclusi, più sotto).
    if (payload.role !== 'REPRESENTATIVE' && payload.role !== 'SECURITY_GUARD') {
      return c.json({ error: 'Solo il Rappresentante o la Guardia di Sicurezza possono decidere' }, 403);
    }

    const classId = await resolveClassId(c);
    const row = await c.env.DB.prepare(
      `SELECT r.id, r.user_id, r.new_first_name, r.new_last_name, r.status,
              u.class_id AS requester_class_id
       FROM name_change_requests r
       JOIN users u ON u.id = r.user_id
       WHERE r.id = ?`
    ).bind(c.req.param('id')).first<{
      id: string;
      user_id: string;
      new_first_name: string;
      new_last_name: string;
      status: string;
      requester_class_id: string | null;
    }>().catch(() => null);

    if (!row || (row.requester_class_id || 'DEFAULT_CLASS') !== classId) {
      return c.json({ error: 'Richiesta non trovata nella tua classe' }, 404);
    }
    if (row.status !== 'PENDING') {
      return c.json({ error: 'Richiesta già decisa' }, 409);
    }
    if (row.user_id === payload.sub) {
      return c.json({ error: 'Sulla tua richiesta decide un altro Rappresentante o la Guardia' }, 403);
    }

    const approved = decision === 'APPROVED';
    const newFirstName = nameCase(row.new_first_name);
    const newLastName = nameCase(row.new_last_name);
    await c.env.DB.batch([
      c.env.DB.prepare(
        'UPDATE name_change_requests SET status = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP WHERE id = ? AND status = ?'
      ).bind(decision, payload.sub, row.id, 'PENDING'),
      ...(approved
        ? [
            c.env.DB.prepare('UPDATE users SET first_name = ?, last_name = ? WHERE id = ?')
              .bind(newFirstName, newLastName, row.user_id),
          ]
        : []),
    ]);

    inBackground(c, notifyUser(
      c.env,
      row.user_id,
      approved ? 'Nome aggiornato' : 'Cambio nome rifiutato',
      approved
        ? `Il tuo nome ora è ${newFirstName} ${newLastName}.`
        : 'La tua richiesta di cambio nome non è stata approvata. Parlene con il Rappresentante.',
      { action: approved ? 'name_change_approved' : 'name_change_rejected' }
    ));

    return c.json({ success: true, status: decision });
  };
}

users.post('/name-requests/:id/approve', nameRequestDecision('APPROVED'));
users.post('/name-requests/:id/reject', nameRequestDecision('REJECTED'));

// ---------------------------------------------------------------------------
// DELETE /api/users/me — Elimina il proprio account
//
// Richiede la password nel corpo: un token rubato o un telefono lasciato sbloccato non devono
// bastare a cancellare un account. Profilo, valutazioni, preferenze, voti, proposte, commenti,
// token push e invii/voti dei sondaggi partono a cascata (ON DELETE CASCADE); gli eventi di
// calendario e le analisi AI restano, con l'autore azzerato (ON DELETE SET NULL). Le uniche
// righe senza cascata sono i registri di sblocco dell'anonimato: si cancellano a mano prima,
// altrimenti il vincolo di chiave esterna bloccherebbe l'eliminazione.
// ---------------------------------------------------------------------------
users.delete('/me', async (c) => {
  const payload = c.get('jwtPayload');

  let password = '';
  try {
    const body = await c.req.json<{ password?: string }>();
    password = body.password ?? '';
  } catch {
    // corpo assente o non JSON: password vuota, respinta sotto
  }
  if (!password) return c.json({ error: 'Password richiesta' }, 400);

  const user = await c.env.DB.prepare('SELECT password_hash FROM users WHERE id = ?')
    .bind(payload.sub)
    .first<{ password_hash: string }>();
  if (!user) return c.json({ error: 'Utente non trovato' }, 404);

  const [storedHash, salt] = user.password_hash.split(':');
  if (!(await verifyPassword(password, storedHash, salt))) {
    return c.json({ error: 'Password non corretta' }, 403);
  }

  await c.env.DB.batch([
    c.env.DB.prepare(
      'DELETE FROM anonymity_unlock_audits WHERE rep_1_id = ?1 OR rep_2_id = ?1 OR security_guard_id = ?1'
    ).bind(payload.sub),
    // Se era la Guardia di Sicurezza della classe, la classe resta senza (lo sceglie di nuovo il
    // Rappresentante): l'id non punta a una riga vera, quindi non c'è una FK a farlo da sé.
    c.env.DB.prepare('UPDATE classes SET security_guard_id = NULL WHERE security_guard_id = ?').bind(payload.sub),
    c.env.DB.prepare('DELETE FROM users WHERE id = ?').bind(payload.sub),
  ]);

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// GET /api/users — Elenco compagni di classe (tutti gli utenti autenticati)
//
// Necessario a TUTTI gli studenti, non solo al Rappresentante: la Mappa Posti
// deve mostrare i nomi dei compagni ad ogni banco e la votazione delle
// Preferenze Sociali deve elencare tutti i possibili destinatari del voto.
// Per privacy, i campi sensibili (username, altezza, priority pass) vengono
// restituiti solo al Rappresentante, che ne ha bisogno per l'algoritmo di
// disposizione; agli altri studenti arriva solo id/nome/cognome/ruolo.
// ---------------------------------------------------------------------------
users.get('/', async (c) => {
  const payload = c.get('jwtPayload');
  const isRepresentative = payload.role === 'REPRESENTATIVE';

  // "Compagni di classe" adesso lo è davvero: prima la query prendeva TUTTI gli utenti
  // registrati, quindi la mappa posti e la votazione delle preferenze elencavano anche gli
  // studenti di altre classi.
  const classId = await resolveClassId(c);

  const rows = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, u.username, u.role, u.created_at,
            sp.height_cm, sp.priority_pass
     FROM users u
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.class_id = ?
     ORDER BY lower(u.last_name), lower(u.first_name)`
  ).bind(classId).all<{
    id: string;
    first_name: string;
    last_name: string;
    username: string;
    role: string;
    created_at: string;
    height_cm: number | null;
    priority_pass: number | null;
  }>();

  return c.json({
    users: rows.results.map((u) => ({
      id: u.id,
      firstName: nameCase(u.first_name),
      lastName: nameCase(u.last_name),
      role: u.role,
      createdAt: u.created_at,
      // Campi visibili solo al Rappresentante (servono al SeatMapOptimizer)
      username: isRepresentative ? u.username : null,
      heightCm: isRepresentative ? u.height_cm : null,
      priorityPass: isRepresentative ? Boolean(u.priority_pass) : null,
    })),
  });
});

export default users;
