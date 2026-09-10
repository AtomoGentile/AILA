// =============================================================================
// CIRCOLARE+ — Users Routes (rotte /api/users)
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, resolveClassId } from '../auth';

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
