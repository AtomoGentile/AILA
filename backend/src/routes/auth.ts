// =============================================================================
// CIRCOLARE+ — Auth Routes (rotte /api/auth)
// =============================================================================

import { Hono } from 'hono';
import type { Env } from '../types';
import { signJWT, hashPassword, verifyPassword, newUUID, normalizeClassLabel, classIdFromLabel } from '../auth';

const auth = new Hono<{ Bindings: Env }>();

// Regola username: 3-20 caratteri, solo lettere/numeri/underscore/punto (nessun dato reale
// richiesto, a differenza dell'email — vedi discussione su raccolta dati minima).
const USERNAME_REGEX = /^[a-zA-Z0-9_.]{3,20}$/;

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
  }>();

  const { firstName, lastName, username, password, heightCm, representativeCode, classLabel } = body;

  // Validation
  if (!firstName || !lastName || !username || !password) {
    return c.json({ error: 'Tutti i campi obbligatori sono richiesti' }, 400);
  }
  if (!USERNAME_REGEX.test(username)) {
    return c.json({ error: 'Lo username deve avere 3-20 caratteri: lettere, numeri, "_" o "."' }, 400);
  }
  if (password.length < 8) {
    return c.json({ error: 'La password deve essere di almeno 8 caratteri' }, 400);
  }
  if (!heightCm || heightCm < 140 || heightCm > 210 || heightCm % 5 !== 0) {
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

  // Ruolo: STUDENT di default. Diventa REPRESENTATIVE solo se chi si registra conosce il codice
  // segreto (impostato via `wrangler secret put REPRESENTATIVE_SIGNUP_CODE`), condiviso a voce/
  // messaggio privato con chi viene eletto rappresentante — nessun pannello admin necessario.
  let role: 'STUDENT' | 'REPRESENTATIVE' = 'STUDENT';
  if (representativeCode && representativeCode.length > 0) {
    if (!c.env.REPRESENTATIVE_SIGNUP_CODE) {
      return c.json({ error: 'Codice rappresentante non configurato sul server' }, 400);
    }
    if (representativeCode !== c.env.REPRESENTATIVE_SIGNUP_CODE) {
      return c.json({ error: 'Codice rappresentante non valido' }, 400);
    }
    role = 'REPRESENTATIVE';
  }

  const usernameLower = username.toLowerCase();

  // Duplicate username check
  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE username = ?')
    .bind(usernameLower)
    .first<{ id: string }>();

  if (existing) {
    return c.json({ error: 'Username già registrato' }, 409);
  }

  // Hash password
  const { hash, salt } = await hashPassword(password);
  // Store as "hash:salt" in password_hash column
  const storedHash = `${hash}:${salt}`;

  const userId = newUUID();

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

  const token = await signJWT(
    { sub: userId, username: usernameLower, role, classId },
    c.env.JWT_SECRET
  );

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

  if (!username || !password) {
    return c.json({ error: 'Username e password richiesti' }, 400);
  }

  const user = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, u.username, u.password_hash, u.role,
            u.class_id, cl.label AS class_label
     FROM users u
     LEFT JOIN classes cl ON cl.id = u.class_id
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
  }>();

  if (!user) {
    return c.json({ error: 'Credenziali non valide' }, 401);
  }

  // Verify password
  const [storedHash, salt] = user.password_hash.split(':');
  const valid = await verifyPassword(password, storedHash, salt);

  if (!valid) {
    return c.json({ error: 'Credenziali non valide' }, 401);
  }

  const classId = user.class_id || 'DEFAULT_CLASS';

  const token = await signJWT(
    { sub: user.id, username: user.username, role: user.role as any, classId },
    c.env.JWT_SECRET
  );

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

  return c.json({
    classes: rows.results.map((r) => ({
      id: r.id,
      label: r.label,
      studentCount: r.student_count,
    })),
  });
});

export default auth;
