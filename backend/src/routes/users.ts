// =============================================================================
// CIRCOLARE+ — Users Routes (rotte /api/users)
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, resolveClassId, verifyPassword } from '../auth';

const users = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

users.use('*', authMiddleware());

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
    firstName: user.first_name,
    lastName: user.last_name,
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
     ORDER BY u.last_name, u.first_name`
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
      firstName: u.first_name,
      lastName: u.last_name,
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
