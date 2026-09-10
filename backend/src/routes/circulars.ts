// =============================================================================
// CIRCOLARE+ — Circulars Routes (/api/circulars/*)
// =============================================================================

import { Hono } from 'hono';
import type { CircularAttachment, Env, JWTPayload } from '../types';
import { authMiddleware } from '../auth';

// `attachments_json` è sempre valido JSON (scritto solo da syncSpaggiariCirculars, default
// '[]' per le righe precedenti alla colonna) — un parse fallito è un bug, non un caso da
// gestire in silenzio, ma non deve far cadere l'intera risposta per le altre circolari.
function parseAttachments(json: string): CircularAttachment[] {
  try {
    return JSON.parse(json);
  } catch {
    return [];
  }
}

const circulars = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

// NB: l'autenticazione viene applicata singolarmente a `/` e `/:number` (sotto), NON con un
// `circulars.use('*', authMiddleware())` globale come prima: quel blanket copriva anche
// `/pdf/:key`, la rotta aperta dal tasto "Apri/Scarica" nel browser di sistema del telefono
// (LocalUriHandler), che non può allegare l'header Authorization — risultato: "chiede
// l'autenticazione" ogni volta. La chiave R2 non è enumerabile/indovinabile, quindi lasciarla
// senza JWT è un compromesso ragionevole (equivalente a un link "chiunque abbia il link").

// ---------------------------------------------------------------------------
// GET /api/circulars — Lista circolari paginata
// ---------------------------------------------------------------------------
circulars.get('/', authMiddleware(), async (c) => {
  const limit = Math.min(parseInt(c.req.query('limit') ?? '50', 10), 100);
  const offset = parseInt(c.req.query('offset') ?? '0', 10);

  const rows = await c.env.DB.prepare(
    'SELECT number, title, publish_date, r2_pdf_key, original_url, attachments_json, created_at FROM circulars ORDER BY number DESC LIMIT ? OFFSET ?'
  ).bind(limit, offset).all<{
    number: number;
    title: string;
    publish_date: string;
    r2_pdf_key: string;
    original_url: string | null;
    attachments_json: string;
    created_at: string;
  }>();

  const total = await c.env.DB.prepare('SELECT COUNT(*) as count FROM circulars')
    .first<{ count: number }>();

  return c.json({
    circulars: rows.results.map((row) => ({
      number: row.number,
      title: row.title,
      publishDate: row.publish_date,
      pdfKey: row.r2_pdf_key,
      hasLocalPdf: true,
      attachments: parseAttachments(row.attachments_json),
      createdAt: row.created_at,
    })),
    total: total?.count ?? 0,
    limit,
    offset,
  });
});

// ---------------------------------------------------------------------------
// GET /api/circulars/:number — Dettaglio singola circolare
// ---------------------------------------------------------------------------
circulars.get('/:number', authMiddleware(), async (c) => {
  const number = parseInt(c.req.param('number') ?? '', 10);
  if (isNaN(number)) return c.json({ error: 'Numero non valido' }, 400);

  const row = await c.env.DB.prepare(
    'SELECT number, title, publish_date, r2_pdf_key, original_url, attachments_json, created_at FROM circulars WHERE number = ?'
  ).bind(number).first<{
    number: number;
    title: string;
    publish_date: string;
    r2_pdf_key: string;
    original_url: string | null;
    attachments_json: string;
    created_at: string;
  }>();

  if (!row) return c.json({ error: 'Circolare non trovata' }, 404);

  return c.json({
    number: row.number,
    title: row.title,
    publishDate: row.publish_date,
    pdfKey: row.r2_pdf_key,
    originalUrl: row.original_url,
    attachments: parseAttachments(row.attachments_json),
    createdAt: row.created_at,
  });
});

// ---------------------------------------------------------------------------
// GET /api/circulars/:number/analysis — Analisi AI in cache (condivisa fra utenti)
// ---------------------------------------------------------------------------
circulars.get('/:number/analysis', authMiddleware(), async (c) => {
  const number = parseInt(c.req.param('number') ?? '', 10);
  if (isNaN(number)) return c.json({ error: 'Numero non valido' }, 400);

  const row = await c.env.DB.prepare(
    'SELECT circular_number, badge, summary, deadlines_json, is_fallback, model_label, updated_at FROM circular_ai_analysis WHERE circular_number = ?'
  ).bind(number).first<{
    circular_number: number;
    badge: string;
    summary: string;
    deadlines_json: string;
    is_fallback: number;
    model_label: string;
    updated_at: string;
  }>();

  if (!row) return c.json({ error: 'Nessuna analisi in cache per questa circolare' }, 404);

  return c.json({
    circularNumber: row.circular_number,
    badge: row.badge,
    summary: row.summary,
    deadlines: JSON.parse(row.deadlines_json),
    isFallback: !!row.is_fallback,
    modelLabel: row.model_label,
    updatedAt: row.updated_at,
  });
});

// ---------------------------------------------------------------------------
// PUT /api/circulars/:number/analysis — Salva/sovrascrive l'analisi AI in cache
// ---------------------------------------------------------------------------
// Chiunque sia autenticato può scrivere qui, senza flusso di approvazione: chi rigenera
// un'analisi di bassa qualità la reinvia e sostituisce quella salvata (vedi commento sulla
// tabella in schema.sql). Non riceve mai il testo del PDF, solo l'esito.
circulars.put('/:number/analysis', authMiddleware(), async (c) => {
  const number = parseInt(c.req.param('number') ?? '', 10);
  if (isNaN(number)) return c.json({ error: 'Numero non valido' }, 400);

  const body = await c.req.json<{
    badge?: string;
    summary?: string;
    deadlines?: unknown[];
    isFallback?: boolean;
    modelLabel?: string;
  }>();

  if (!body.badge || !['RELEVANT', 'POTENTIAL', 'NOT_RELEVANT'].includes(body.badge)) {
    return c.json({ error: 'badge non valido' }, 400);
  }
  if (!body.summary || typeof body.summary !== 'string') {
    return c.json({ error: 'summary mancante' }, 400);
  }

  const payload = c.get('jwtPayload') as JWTPayload;
  const deadlinesJson = JSON.stringify(body.deadlines ?? []);

  await c.env.DB.prepare(
    `INSERT INTO circular_ai_analysis
       (circular_number, badge, summary, deadlines_json, is_fallback, model_label, submitted_by, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(circular_number) DO UPDATE SET
       badge = excluded.badge,
       summary = excluded.summary,
       deadlines_json = excluded.deadlines_json,
       is_fallback = excluded.is_fallback,
       model_label = excluded.model_label,
       submitted_by = excluded.submitted_by,
       updated_at = CURRENT_TIMESTAMP`
  ).bind(
    number,
    body.badge,
    body.summary,
    deadlinesJson,
    body.isFallback ? 1 : 0,
    body.modelLabel ?? 'Sconosciuto',
    payload.sub
  ).run();

  return c.json({ success: true });
});

// ---------------------------------------------------------------------------
// GET /api/circulars/pdf/:key+ — Proxy PDF da R2
// ---------------------------------------------------------------------------
circulars.get('/pdf/:key{.+}', async (c) => {
  const key = c.req.param('key');
  const object = await c.env.CIRCULARS_BUCKET.get(key);

  if (!object) {
    return new Response('PDF non trovato', { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('etag', object.httpEtag);
  headers.set('Content-Type', 'application/pdf');
  headers.set('Cache-Control', 'public, max-age=86400');

  return new Response(object.body, { headers });
});

export default circulars;
