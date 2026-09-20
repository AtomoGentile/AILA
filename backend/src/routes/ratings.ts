// =============================================================================
// CIRCOLARE+ — Ratings Routes (/api/ratings/*)
// Valutazioni riservate del Rappresentante (1-5 didattico, comportamento, priority pass)
// =============================================================================

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, resolveClassId } from '../auth';

const ratings = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

ratings.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// GET /api/ratings — Lista tutti i rating (solo REPRESENTATIVE)
//
// Include anche il Rappresentante stesso, non solo gli STUDENT: il Priority Pass (righe 0-2)
// è un vincolo del posto a sedere, non un privilegio esclusivo degli studenti — anche il
// Rappresentante può averne bisogno, e prima ne era escluso per costruzione (query filtrata su
// STUDENT), quindi non appariva mai nella Scheda Classe e il suo `priority_pass` non veniva mai
// letto dal SeatMapOptimizer (che quindi non lo avvicinava mai alla prima fila anche se serviva).
// ---------------------------------------------------------------------------
ratings.get('/', requireRole('REPRESENTATIVE'), async (c) => {
  const rows = await c.env.DB.prepare(
    `SELECT u.id, u.first_name, u.last_name, u.role,
            (cl.security_guard_id = u.id) AS is_guard,
            rr.didactic, rr.behavior, rr.updated_at,
            sp.priority_pass, sp.height_cm
     FROM users u
     LEFT JOIN classes cl ON cl.id = u.class_id
     LEFT JOIN representative_ratings rr ON rr.student_id = u.id
     LEFT JOIN student_profiles sp ON sp.user_id = u.id
     WHERE u.role IN ('STUDENT', 'REPRESENTATIVE') AND u.class_id = ?
     ORDER BY u.last_name, u.first_name`
  ).bind(await resolveClassId(c)).all<{
    id: string;
    first_name: string;
    last_name: string;
    role: string;
    is_guard: number | null;
    didactic: number | null;
    behavior: number | null;
    updated_at: string | null;
    priority_pass: number | null;
    height_cm: number | null;
  }>();

  return c.json({
    ratings: rows.results.map((r) => ({
      studentId: r.id,
      firstName: r.first_name,
      lastName: r.last_name,
      role: r.role,
      // Guardia di Sicurezza della classe (terza firma per lo svelamento): la nomina il
      // Rappresentante da questa stessa schermata.
      isSecurityGuard: Boolean(r.is_guard),
      heightCm: r.height_cm,
      didactic: r.didactic,
      behavior: r.behavior,
      priorityPass: Boolean(r.priority_pass),
      updatedAt: r.updated_at,
    })),
  });
});

// ---------------------------------------------------------------------------
// PUT /api/ratings/:studentId — Imposta rating didattico e comportamento
// ---------------------------------------------------------------------------
ratings.put('/:studentId', requireRole('REPRESENTATIVE'), async (c) => {
  const studentId = c.req.param('studentId');
  const body = await c.req.json<{ didactic?: number | null; behavior?: number | null }>();
  // Il client (Kotlin/kotlinx.serialization) serializza sempre entrambi i campi, usando `null`
  // per "non toccare questo valore" quando si aggiorna un solo rating alla volta (es. lo
  // stepper Didattica non deve azzerare Comportamento). Normalizziamo null a undefined così
  // "non fornito" ha un solo significato in tutta la route.
  const didactic = body.didactic ?? undefined;
  const behavior = body.behavior ?? undefined;

  // Validate
  if (didactic !== undefined && (didactic < 1 || didactic > 5 || !Number.isInteger(didactic))) {
    return c.json({ error: 'didactic deve essere un intero tra 1 e 5' }, 400);
  }
  if (behavior !== undefined && (behavior < 1 || behavior > 5 || !Number.isInteger(behavior))) {
    return c.json({ error: 'behavior deve essere un intero tra 1 e 5' }, 400);
  }

  // Check student exists
  // AND class_id: un rappresentante non valuta (né dà il pass) agli studenti di altre classi.
  const student = await c.env.DB.prepare("SELECT id FROM users WHERE id = ? AND class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')")
    .bind(studentId, await resolveClassId(c)).first<{ id: string }>();
  if (!student) return c.json({ error: 'Studente non trovato in questa classe' }, 404);

  // Must have at least one of the two
  const existing = await c.env.DB.prepare('SELECT student_id FROM representative_ratings WHERE student_id = ?')
    .bind(studentId).first<{ student_id: string }>();

  if (existing) {
    await c.env.DB.prepare(
      `UPDATE representative_ratings SET
         didactic = COALESCE(?, didactic),
         behavior = COALESCE(?, behavior),
         updated_at = CURRENT_TIMESTAMP
       WHERE student_id = ?`
    ).bind(didactic ?? null, behavior ?? null, studentId).run();
  } else {
    // Prima inserzione: il campo non fornito parte da 3 (valore neutro), così anche impostando
    // un solo valore alla volta (come fa la Scheda Classe, uno stepper indipendente per campo)
    // non serve inserirli per forza insieme.
    await c.env.DB.prepare(
      'INSERT INTO representative_ratings (student_id, didactic, behavior) VALUES (?, ?, ?)'
    ).bind(studentId, didactic ?? 3, behavior ?? 3).run();
  }

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// PUT /api/ratings/:studentId/priority-pass — Toggle priority pass
// ---------------------------------------------------------------------------
ratings.put('/:studentId/priority-pass', requireRole('REPRESENTATIVE'), async (c) => {
  const studentId = c.req.param('studentId');
  const { enabled } = await c.req.json<{ enabled: boolean }>();

  // AND class_id: un rappresentante non valuta (né dà il pass) agli studenti di altre classi.
  const student = await c.env.DB.prepare("SELECT id FROM users WHERE id = ? AND class_id = ? AND role IN ('STUDENT', 'REPRESENTATIVE')")
    .bind(studentId, await resolveClassId(c)).first<{ id: string }>();
  if (!student) return c.json({ error: 'Studente non trovato in questa classe' }, 404);

  await c.env.DB.prepare(
    'UPDATE student_profiles SET priority_pass = ? WHERE user_id = ?'
  ).bind(enabled ? 1 : 0, studentId).run();

  return c.json({ success: true, priorityPass: enabled });
});

export default ratings;
