// =============================================================================
// CIRCOLARE+ — Auth Routes (rotte /api/auth)
// =============================================================================

import { Hono } from 'hono';
import type { Env, UserRole } from '../types';
import {
  classIdFromLabel,
  issueToken,
  newUUID,
  normalizeClassLabel,
  passwordProblem,
  sha256Hex,
  storedPasswordHash,
  timingSafeEqual,
  verifyPassword,
} from '../auth';
import { clearAttempts, clientIp, isRateLimited, recordAttempt } from '../rateLimit';
import { classInviteCode } from '../services/classInvites';
import { inBackground } from '../services/background';
import { notifyUsers } from '../services/fcm';
import {
  checkRepresentativeInvite,
  claimRepresentativeInvite,
  matchesGlobalCode,
  releaseRepresentativeInvite,
} from '../services/representativeInvites';

const auth = new Hono<{ Bindings: Env }>();

// Regola username: 3-20 caratteri, solo lettere/numeri/underscore/punto (nessun dato reale
// richiesto, a differenza dell'email — vedi discussione su raccolta dati minima).
const USERNAME_REGEX = /^[a-zA-Z0-9_.]{3,20}$/;
const MAX_NAME_LENGTH = 40;
// Non piu' di due Rappresentanti per classe: il quorum per svelare un anonimo e' "2
// Rappresentanti + la Guardia", e con un terzo account rappresentante una persona sola potrebbe
// coprire due firme.
const MAX_REPRESENTATIVES_PER_CLASS = 2;

// Finestre dei limiti ai tentativi (vedi rateLimit.ts).
const LOGIN_WINDOW = 15 * 60;
const LOGIN_MAX_FAILURES_PER_USER = 8;
const LOGIN_MAX_FAILURES_PER_IP = 40;
const REGISTER_WINDOW = 60 * 60;
const REGISTER_MAX_PER_IP = 10;
const RESET_WINDOW = 15 * 60;
const RESET_MAX_FAILURES = 6;

const TOO_MANY = 'Troppi tentativi: riprova fra qualche minuto';

/** Notifica ai Rappresentanti e alla Guardia della classe, tranne il nuovo arrivato. */
async function notifyNewRepresentative(env: Env, classId: string, newUserId: string, fullName: string, classLabel: string) {
  const rows = await env.DB.prepare(
    `SELECT u.id FROM users u JOIN classes cl ON cl.id = u.class_id
     WHERE u.class_id = ? AND u.id != ? AND (u.role = 'REPRESENTATIVE' OR u.id = cl.security_guard_id)`
  ).bind(classId, newUserId).all<{ id: string }>();
  await notifyUsers(
    env,
    rows.results.map((r) => r.id),
    'Nuovo Rappresentante in classe',
    `${fullName} si è registrato come Rappresentante della ${classLabel}. Se non te lo aspettavi, avvisa subito chi gestisce l'app.`,
    { action: 'representative_joined', user_id: newUserId }
  );
}

// ---------------------------------------------------------------------------
// POST /api/auth/register
// ---------------------------------------------------------------------------
auth.post('/register', async (c) => {
  const body = await c.req.json<{
    firstName: string;
    lastName: string;
    username: string;
    password: string;
    heightCm: number;
    representativeCode?: string;
    // Classe scelta in fase di registrazione. Prima non esisteva: la classe era una sola,
    // implicita nel server, e chiunque si registrasse finiva a vedere i contenuti di quella.
    classLabel?: string;
    // Codice della classe (lo vede il Rappresentante nella Scheda Classe): serve per entrare in
    // una classe che ha gia' degli iscritti.
    classCode?: string;
  }>();

  const { username, password, heightCm, representativeCode, classLabel, classCode } = body;
  const firstName = typeof body.firstName === 'string' ? body.firstName.trim() : '';
  const lastName = typeof body.lastName === 'string' ? body.lastName.trim() : '';

  const ip = clientIp(c.req);
  if (ip) {
    if (await isRateLimited(c.env, `register:ip:${ip}`, REGISTER_MAX_PER_IP, REGISTER_WINDOW)) {
      return c.json({ error: TOO_MANY }, 429);
    }
    await recordAttempt(c.env, `register:ip:${ip}`, REGISTER_WINDOW);
  }

  // Validation
  if (!firstName || !lastName || typeof username !== 'string' || typeof password !== 'string') {
    return c.json({ error: 'Tutti i campi obbligatori sono richiesti' }, 400);
  }
  if (firstName.length > MAX_NAME_LENGTH || lastName.length > MAX_NAME_LENGTH) {
    return c.json({ error: `Nome e cognome possono avere al massimo ${MAX_NAME_LENGTH} caratteri` }, 400);
  }
  if (!USERNAME_REGEX.test(username)) {
    return c.json({ error: 'Lo username deve avere 3-20 caratteri: lettere, numeri, "_" o "."' }, 400);
  }
  const weakPassword = passwordProblem(password);
  if (weakPassword) {
    return c.json({ error: weakPassword }, 400);
  }
  if (typeof heightCm !== 'number' || heightCm < 140 || heightCm > 210 || heightCm % 5 !== 0) {
    return c.json({ error: "L'altezza deve essere tra 140 e 210 cm a scaglioni di 5 cm" }, 400);
  }

  // Classe: obbligatoria. L'etichetta viene normalizzata ("4csa", "4^ CSA" e "4 CSA" sono la
  // stessa classe) e, se non esiste ancora, la classe viene creata: è il primo studente di
  // quella classe a portarla in vita, senza bisogno di un pannello di amministrazione.
  if (!classLabel || !classLabel.trim()) {
    return c.json({ error: 'Indica la tua classe (per esempio 4 CSA)' }, 400);
  }
  const normalizedLabel = normalizeClassLabel(classLabel);
  if (!normalizedLabel) {
    return c.json(
      { error: 'Classe non valida. Usa il formato anno + sezione, per esempio "4 CSA" o "3 B".' },
      400
    );
  }
  const classId = classIdFromLabel(normalizedLabel);

  // Ruolo: STUDENT di default. Diventa REPRESENTATIVE con un codice Rappresentante emesso per
  // questa classe (POST /api/admin/representative-invites, vedi README): vale una volta sola e
  // scade. Prima il codice era uno per tutta la scuola, e chi lo conosceva poteva registrare da solo
  // il secondo Rappresentante della propria classe (o diventare il primo di una classe che non ne
  // aveva) e, nominando la Guardia, avere le tre firme che svelano gli anonimi.
  const members = await c.env.DB.prepare(
    `SELECT COUNT(*) AS total, SUM(CASE WHEN role = 'REPRESENTATIVE' THEN 1 ELSE 0 END) AS reps
     FROM users WHERE class_id = ?`
  ).bind(classId).first<{ total: number; reps: number | null }>();
  const reps = members?.reps ?? 0;

  let role: 'STUDENT' | 'REPRESENTATIVE' = 'STUDENT';
  let inviteId: string | null = null;
  if (representativeCode !== undefined && representativeCode !== null && representativeCode !== '') {
    if (typeof representativeCode !== 'string') {
      return c.json({ error: 'Codice Rappresentante non valido' }, 400);
    }
    if (matchesGlobalCode(c.env, representativeCode)) {
      // Codice unico di prima, solo durante la transizione e solo dove non c'e' ancora nessun
      // Rappresentante: il secondo, quello che chiude il quorum, entra solo con un codice di classe.
      if (reps > 0) {
        return c.json({
          error: 'Questa classe ha già un Rappresentante: serve un codice Rappresentante emesso per la classe',
        }, 400);
      }
    } else {
      const check = await checkRepresentativeInvite(c.env, representativeCode, classId);
      if (!check.ok) return c.json({ error: check.error }, check.status);
      inviteId = check.id;
    }
    role = 'REPRESENTATIVE';
  }
  if (role === 'REPRESENTATIVE' && reps >= MAX_REPRESENTATIVES_PER_CLASS) {
    return c.json({ error: `La classe ha già ${MAX_REPRESENTATIVES_PER_CLASS} Rappresentanti` }, 409);
  }

  // Classe gia' esistente con degli iscritti: per entrarci da studente serve il suo codice, che ha
  // il Rappresentante. Prima bastava scrivere "4 CSA" per vedere nomi, bacheca e calendario della
  // classe. Il Rappresentante non lo deve dare: il suo codice vale gia' solo per questa classe.
  if (role === 'STUDENT' && (members?.total ?? 0) > 0) {
    // Con un Rappresentante in classe il codice si crea qui se manca: prima nasceva solo quando
    // lui apriva la Scheda Classe, e fino ad allora chiunque scrivesse "4 CSA" entrava. Chi resta
    // fuori lo chiede al Rappresentante, che lo trova gia' pronto. Senza Rappresentanti non c'e'
    // nessuno che possa darlo, quindi la classe resta aperta finche' non ne arriva uno.
    const expected = await classInviteCode(c.env, classId, { createIfMissing: reps > 0 });
    if (expected !== null) {
      const given = (classCode ?? '').trim().toUpperCase().replace(/[\s-]/g, '');
      if (!given) {
        return c.json({
          error: 'Per entrare in questa classe serve il codice classe: chiedilo al Rappresentante (lo trova nella Scheda Classe)',
          classCodeRequired: true,
        }, 403);
      }
      if (!timingSafeEqual(given, expected)) {
        return c.json({ error: 'Codice classe non valido', classCodeRequired: true }, 403);
      }
    }
  }

  const usernameLower = username.toLowerCase();

  // Duplicate username check
  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE username = ?')
    .bind(usernameLower)
    .first<{ id: string }>();

  if (existing) {
    return c.json({ error: 'Username già registrato' }, 409);
  }

  // Salvata come "hash:salt" nella colonna password_hash
  const storedHash = await storedPasswordHash(password);

  const userId = newUUID();

  // Il codice si segna come usato prima di creare l'account: se due registrazioni lo usano insieme
  // ne passa una sola. Se poi la creazione fallisce, il codice torna libero.
  if (inviteId && !(await claimRepresentativeInvite(c.env, inviteId, userId))) {
    return c.json({ error: 'Codice Rappresentante già usato: ne serve uno nuovo' }, 400);
  }

  try {
    await c.env.DB.batch([
      // OR IGNORE: se due studenti della stessa classe si registrano nello stesso momento, la
      // seconda INSERT non deve far fallire la registrazione.
      c.env.DB.prepare(
        'INSERT OR IGNORE INTO classes (id, label) VALUES (?, ?)'
      ).bind(classId, normalizedLabel),

      c.env.DB.prepare(
        'INSERT INTO users (id, first_name, last_name, username, password_hash, role, class_id) VALUES (?, ?, ?, ?, ?, ?, ?)'
      ).bind(userId, firstName, lastName, usernameLower, storedHash, role, classId),

      c.env.DB.prepare(
        'INSERT INTO student_profiles (user_id, height_cm) VALUES (?, ?)'
      ).bind(userId, heightCm),
    ]);
  } catch (err) {
    if (inviteId) await releaseRepresentativeInvite(c.env, inviteId, userId);
    throw err;
  }

  if (role === 'REPRESENTATIVE') {
    // Avvisa gli altri Rappresentanti e la Guardia della classe: un Rappresentante in piu' e' una
    // firma in piu' nel quorum che svela gli anonimi, e chi non se lo aspettava lo deve sapere.
    inBackground(c, notifyNewRepresentative(c.env, classId, userId, `${firstName} ${lastName}`, normalizedLabel));
  }

  const token = await issueToken(c.env, { id: userId, username: usernameLower, role, classId, passwordHash: storedHash });

  return c.json({
    success: true,
    token,
    user: {
      id: userId,
      firstName,
      lastName,
      username: usernameLower,
      role,
      heightCm,
      classId,
      classLabel: normalizedLabel,
    },
  }, 201);
});

// ---------------------------------------------------------------------------
// POST /api/auth/login
// ---------------------------------------------------------------------------
auth.post('/login', async (c) => {
  const body = await c.req.json<{ username: string; password: string }>();
  const { username, password } = body;

  if (typeof username !== 'string' || typeof password !== 'string' || !username || !password) {
    return c.json({ error: 'Username e password richiesti' }, 400);
  }

  // Limite ai tentativi sbagliati, per username e per indirizzo: senza, una password si poteva
  // provare all'infinito.
  const userKey = `login:user:${username.toLowerCase()}`;
  const ip = clientIp(c.req);
  const ipKey = ip ? `login:ip:${ip}` : null;
  if (
    (await isRateLimited(c.env, userKey, LOGIN_MAX_FAILURES_PER_USER, LOGIN_WINDOW)) ||
    (ipKey && (await isRateLimited(c.env, ipKey, LOGIN_MAX_FAILURES_PER_IP, LOGIN_WINDOW)))
  ) {
    return c.json({ error: TOO_MANY }, 429);
  }
  const failed = async () => {
    await recordAttempt(c.env, userKey, LOGIN_WINDOW);
    if (ipKey) await recordAttempt(c.env, ipKey, LOGIN_WINDOW);
    return c.json({ error: 'Credenziali non valide' }, 401);
  };

  const user = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, u.username, u.password_hash, u.role,
            u.class_id, cl.label AS class_label,
            sp.height_cm, sp.priority_pass, sp.notification_board_enabled
     FROM users u
     LEFT JOIN classes cl ON cl.id = u.class_id
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.username = ?`
  ).bind(username.toLowerCase()).first<{
    id: string;
    first_name: string;
    last_name: string;
    username: string;
    password_hash: string;
    role: string;
    class_id: string | null;
    class_label: string | null;
    height_cm: number | null;
    priority_pass: number | null;
    notification_board_enabled: number | null;
  }>();

  if (!user) {
    // Stesso lavoro di una password sbagliata: altrimenti dal tempo di risposta si capiva quali
    // username esistono.
    await verifyPassword(password, '', 'nessun-utente');
    return failed();
  }

  // Verify password
  const [storedHash, salt] = user.password_hash.split(':');
  const valid = await verifyPassword(password, storedHash, salt);

  if (!valid) {
    return failed();
  }
  await clearAttempts(c.env, userKey);

  const classId = user.class_id || 'DEFAULT_CLASS';

  const token = await issueToken(c.env, {
    id: user.id,
    username: user.username,
    role: user.role as UserRole,
    classId,
    passwordHash: user.password_hash,
  });

  return c.json({
    success: true,
    token,
    user: {
      id: user.id,
      firstName: user.first_name,
      lastName: user.last_name,
      username: user.username,
      role: user.role,
      classId,
      classLabel: user.class_label,
      heightCm: user.height_cm,
      priorityPass: Boolean(user.priority_pass),
      notificationBoardEnabled: Boolean(user.notification_board_enabled ?? 1),
    },
  });
});

// ---------------------------------------------------------------------------
// GET /api/auth/classes — Elenco delle classi già esistenti
//
// Pubblica di proposito: serve alla schermata di registrazione, cioè prima di avere un token.
// Non espone nulla di sensibile — solo l'etichetta della classe e quanti si sono registrati —
// e permette di scegliere dall'elenco invece di riscrivere l'etichetta a mano (che è il modo
// più veloce per ritrovarsi due classi "4 CSA" e "4CSA" separate).
// ---------------------------------------------------------------------------
auth.get('/classes', async (c) => {
  const rows = await c.env.DB.prepare(
    `SELECT cl.id, cl.label, COUNT(u.id) AS student_count
     FROM classes cl
     LEFT JOIN users u ON u.class_id = cl.id
     GROUP BY cl.id, cl.label
     ORDER BY cl.label`
  ).all<{ id: string; label: string; student_count: number }>();

  // Solo le classi con etichetta "anno + sezione": la classe di prima del multi-classe
  // (DEFAULT_CLASS) non e' scegliibile, la registrazione la rifiuterebbe.
  return c.json({
    classes: rows.results.filter((r) => normalizeClassLabel(r.label) !== null).map((r) => ({
      id: r.id,
      label: r.label,
      studentCount: r.student_count,
    })),
  });
});

// ---------------------------------------------------------------------------
// POST /api/auth/reset-password — Nuova password con il codice dato dal Rappresentante
//
// Nell'app non c'e' un'email con cui recuperare la password: il Rappresentante genera per il
// compagno un codice monouso (POST /api/users/:id/reset-code) e glielo dice. Il codice vale 24
// ore, una volta sola; i tentativi sbagliati sono limitati.
// ---------------------------------------------------------------------------
auth.post('/reset-password', async (c) => {
  const body = await c.req.json<{ username?: string; code?: string; newPassword?: string }>();
  const username = typeof body.username === 'string' ? body.username.trim().toLowerCase() : '';
  const code = typeof body.code === 'string' ? body.code.trim().toUpperCase().replace(/[\s-]/g, '') : '';
  if (!username || !code) return c.json({ error: 'Username e codice sono obbligatori' }, 400);
  const weakPassword = passwordProblem(body.newPassword);
  if (weakPassword) return c.json({ error: weakPassword }, 400);

  const key = `reset:user:${username}`;
  if (await isRateLimited(c.env, key, RESET_MAX_FAILURES, RESET_WINDOW)) {
    return c.json({ error: TOO_MANY }, 429);
  }

  const row = await c.env.DB.prepare(
    `SELECT u.id, u.username, u.role, u.class_id, u.first_name, u.last_name, cl.label AS class_label,
            sp.height_cm, sp.priority_pass, sp.notification_board_enabled,
            r.code_hash, r.expires_at
     FROM users u
     JOIN password_reset_codes r ON r.user_id = u.id
     LEFT JOIN classes cl ON cl.id = u.class_id
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.username = ?`
  ).bind(username).first<{
    id: string;
    username: string;
    role: UserRole;
    class_id: string | null;
    first_name: string;
    last_name: string;
    class_label: string | null;
    height_cm: number | null;
    priority_pass: number | null;
    notification_board_enabled: number | null;
    code_hash: string;
    expires_at: number;
  }>().catch(() => null);

  const valid = row !== null && row.expires_at > Math.floor(Date.now() / 1000) && row.code_hash === (await sha256Hex(code));
  if (!row || !valid) {
    await recordAttempt(c.env, key, RESET_WINDOW);
    return c.json({ error: 'Codice non valido o scaduto' }, 400);
  }

  const passwordHash = await storedPasswordHash(body.newPassword as string);
  await c.env.DB.batch([
    c.env.DB.prepare('UPDATE users SET password_hash = ? WHERE id = ?').bind(passwordHash, row.id),
    c.env.DB.prepare('DELETE FROM password_reset_codes WHERE user_id = ?').bind(row.id),
  ]);
  await clearAttempts(c.env, key);
  await clearAttempts(c.env, `login:user:${username}`);

  const classId = row.class_id || 'DEFAULT_CLASS';
  const token = await issueToken(c.env, { id: row.id, username: row.username, role: row.role, classId, passwordHash });
  return c.json({
    success: true,
    token,
    user: {
      id: row.id,
      firstName: row.first_name,
      lastName: row.last_name,
      username: row.username,
      role: row.role,
      classId,
      classLabel: row.class_label,
      heightCm: row.height_cm,
      priorityPass: Boolean(row.priority_pass),
      notificationBoardEnabled: Boolean(row.notification_board_enabled ?? 1),
    },
  });
});

export default auth;
