// =============================================================================
// AILA — Admin Routes (rotte /api/admin)
//
// Solo per chi gestisce il Worker: niente token utente, serve l'header `X-Admin-Secret` uguale al
// secret ADMIN_SECRET (`wrangler secret put ADMIN_SECRET`). Nessun ruolo dell'app le apre, nemmeno
// il Rappresentante: un Rappresentante che emette il codice per la propria classe e' proprio il
// caso da evitare (vedi services/representativeInvites.ts). Uso dal terminale: README, "Codici
// Rappresentante".
// =============================================================================

import { Hono } from 'hono';
import type { Env } from '../types';
import { classIdFromLabel, normalizeClassLabel, timingSafeEqual } from '../auth';
import { clientIp, isRateLimited, recordAttempt } from '../rateLimit';
import {
  INVITE_DEFAULT_TTL_DAYS,
  INVITE_MAX_COUNT,
  INVITE_MAX_TTL_DAYS,
  issueRepresentativeInvites,
} from '../services/representativeInvites';

const admin = new Hono<{ Bindings: Env }>();

const MIN_SECRET_LENGTH = 16;
const ADMIN_WINDOW = 15 * 60;
const ADMIN_MAX_FAILURES_PER_IP = 10;

admin.use('*', async (c, next) => {
  const secret = c.env.ADMIN_SECRET;
  if (!secret || secret.length < MIN_SECRET_LENGTH) {
    return c.json({ error: `ADMIN_SECRET non configurato sul Worker (almeno ${MIN_SECRET_LENGTH} caratteri)` }, 503);
  }
  const ip = clientIp(c.req);
  const key = ip ? `admin:ip:${ip}` : null;
  if (key && (await isRateLimited(c.env, key, ADMIN_MAX_FAILURES_PER_IP, ADMIN_WINDOW))) {
    return c.json({ error: 'Troppi tentativi: riprova fra qualche minuto' }, 429);
  }
  const given = c.req.header('X-Admin-Secret') ?? '';
  if (!timingSafeEqual(given, secret)) {
    if (key) await recordAttempt(c.env, key, ADMIN_WINDOW);
    return c.json({ error: 'Non autorizzato' }, 401);
  }
  await next();
});

// ---------------------------------------------------------------------------
// POST /api/admin/representative-invites — Nuovi codici Rappresentante per una classe
//
// Corpo: { classLabel: "4 CSA", count?: 1-2 (default 1), ttlDays?: 1-30 (default 7) }. Con
// count 2 escono due codici diversi, uno per Rappresentante. I codici in chiaro ci sono solo in
// questa risposta: sul database resta l'hash.
// ---------------------------------------------------------------------------
admin.post('/representative-invites', async (c) => {
  const body = await c.req.json<{ classLabel?: unknown; ttlDays?: unknown; count?: unknown }>();
  const label = typeof body.classLabel === 'string' ? normalizeClassLabel(body.classLabel) : null;
  if (!label) {
    return c.json({ error: 'Classe non valida. Usa il formato anno + sezione, per esempio "4 CSA".' }, 400);
  }
  const ttlDays = body.ttlDays === undefined || body.ttlDays === null ? INVITE_DEFAULT_TTL_DAYS : body.ttlDays;
  if (typeof ttlDays !== 'number' || !Number.isInteger(ttlDays) || ttlDays < 1 || ttlDays > INVITE_MAX_TTL_DAYS) {
    return c.json({ error: `ttlDays deve essere un numero intero da 1 a ${INVITE_MAX_TTL_DAYS}` }, 400);
  }
  const count = body.count === undefined || body.count === null ? 1 : body.count;
  if (typeof count !== 'number' || !Number.isInteger(count) || count < 1 || count > INVITE_MAX_COUNT) {
    return c.json({ error: `count deve essere un numero intero da 1 a ${INVITE_MAX_COUNT}` }, 400);
  }

  const classId = classIdFromLabel(label);
  let issued;
  try {
    issued = await issueRepresentativeInvites(c.env, classId, ttlDays, count);
  } catch {
    return c.json({ error: 'Codici Rappresentante non ancora attivi sul server (manca la migrazione 017)' }, 503);
  }

  const reps = await c.env.DB.prepare(
    "SELECT COUNT(*) AS n FROM users WHERE class_id = ? AND role = 'REPRESENTATIVE'"
  ).bind(classId).first<{ n: number }>();

  return c.json({
    codes: issued.codes,
    classId,
    classLabel: label,
    expiresAt: new Date(issued.expiresAt * 1000).toISOString(),
    // Per accorgersi subito se la classe ha gia' i suoi due Rappresentanti (il codice non
    // servirebbe: la registrazione risponde 409).
    representatives: reps?.n ?? 0,
  }, 201);
});

// ---------------------------------------------------------------------------
// GET /api/admin/representative-invites?classLabel=4%20CSA — Codici emessi (senza il codice)
// ---------------------------------------------------------------------------
admin.get('/representative-invites', async (c) => {
  const raw = c.req.query('classLabel');
  const label = raw ? normalizeClassLabel(raw) : null;
  if (raw && !label) return c.json({ error: 'Classe non valida' }, 400);
  const rows = await c.env.DB.prepare(
    `SELECT i.id, i.class_id, i.expires_at, i.used_by, i.used_at, i.created_at, u.username AS used_by_username
     FROM representative_invites i
     LEFT JOIN users u ON u.id = i.used_by
     ${label ? 'WHERE i.class_id = ?' : ''}
     ORDER BY i.created_at DESC
     LIMIT 200`
  ).bind(...(label ? [classIdFromLabel(label)] : [])).all<{
    id: string;
    class_id: string;
    expires_at: number;
    used_by: string | null;
    used_at: number | null;
    created_at: string;
    used_by_username: string | null;
  }>();
  return c.json({
    invites: rows.results.map((r) => ({
      id: r.id,
      classId: r.class_id,
      // Inverso di classIdFromLabel ("CLASS_4_CSA" -> "4 CSA").
      classLabel: r.class_id.replace(/^CLASS_/, '').replace(/_/g, ' '),
      expiresAt: new Date(r.expires_at * 1000).toISOString(),
      usedBy: r.used_by_username ?? r.used_by,
      usedAt: r.used_at ? new Date(r.used_at * 1000).toISOString() : null,
      // CURRENT_TIMESTAMP di SQLite e' UTC senza fuso: "2026-10-03 21:00:00".
      createdAt: r.created_at ? new Date(`${r.created_at.replace(' ', 'T')}Z`).toISOString() : null,
    })),
  });
});

// ---------------------------------------------------------------------------
// DELETE /api/admin/representative-invites/:id — Ritira un codice non ancora usato
// ---------------------------------------------------------------------------
admin.delete('/representative-invites/:id', async (c) => {
  const res = await c.env.DB.prepare('DELETE FROM representative_invites WHERE id = ? AND used_by IS NULL')
    .bind(c.req.param('id')).run();
  if ((res.meta?.changes ?? 0) === 0) return c.json({ error: 'Codice non trovato o già usato' }, 404);
  return c.json({ success: true });
});

export default admin;
