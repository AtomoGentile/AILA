// =============================================================================
// CIRCOLARE+ — Circulars Routes (/api/circulars/*)
// =============================================================================

import { Hono } from 'hono';
import type { CircularAttachment, Env, JWTPayload } from '../types';
import { authMiddleware } from '../auth';
import { BACKFILL_WINDOW, MAX_ATTEMPTS, upsertAnalysis } from '../services/summarizer';

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

interface AnalysisRow {
  circular_number: number;
  badge: string;
  summary: string;
  deadlines_json: string;
  is_fallback: number;
  model_label: string;
  tier: number;
  updated_at: string;
}

const ANALYSIS_COLUMNS =
  'circular_number, badge, summary, deadlines_json, is_fallback, model_label, tier, updated_at';

function analysisJson(row: AnalysisRow) {
  return {
    circularNumber: row.circular_number,
    badge: row.badge,
    summary: row.summary,
    deadlines: JSON.parse(row.deadlines_json),
    isFallback: !!row.is_fallback,
    modelLabel: row.model_label,
    tier: row.tier,
    updatedAt: row.updated_at,
  };
}

// Livello di qualità deciso dal server, non dal client: 0 ripiego euristico, 1 AI locale,
// 2 Gemini. Vedi la migrazione 006.
function tierOf(isFallback: boolean, modelLabel: string): number {
  if (isFallback) return 0;
  return modelLabel.startsWith('Google Gemini') ? 2 : 1;
}

// ---------------------------------------------------------------------------
// GET /api/circulars/newer?after=N — Circolari con numero > N
// ---------------------------------------------------------------------------
// Usata dal refresh in background di iOS (BGTaskScheduler): risposta minima per restare veloce.
// Registrata prima di `/:number`, che altrimenti catturerebbe "newer" come numero.
circulars.get('/newer', authMiddleware(), async (c) => {
  const after = parseInt(c.req.query('after') ?? '0', 10);
  if (isNaN(after) || after < 0) return c.json({ error: 'Parametro after non valido' }, 400);

  const rows = await c.env.DB.prepare(
    'SELECT number, title, publish_date FROM circulars WHERE number > ? ORDER BY number ASC LIMIT 50'
  ).bind(after).all<{ number: number; title: string; publish_date: string }>();

  return c.json({
    circulars: rows.results.map((row) => ({
      number: row.number,
      title: row.title,
      publishDate: row.publish_date,
    })),
  });
});

// ---------------------------------------------------------------------------
// GET /api/circulars/analyses?since=... — Analisi cambiate dopo `since`
// ---------------------------------------------------------------------------
// Chi ha l'app aperta la chiama insieme al rinfresco della lista: così vede subito un riassunto
// fatto da un compagno o dal server, e un'analisi locale in corso sulla stessa circolare si ferma.
// Registrata prima di `/:number`, che altrimenti catturerebbe "analyses" come numero.
circulars.get('/analyses', authMiddleware(), async (c) => {
  const since = c.req.query('since') ?? '1970-01-01 00:00:00';
  const rows = await c.env.DB.prepare(
    `SELECT ${ANALYSIS_COLUMNS} FROM circular_ai_analysis
     WHERE updated_at > ? AND tier > 0 ORDER BY updated_at ASC LIMIT 200`
  ).bind(since).all<AnalysisRow>();

  // Cosa fa il server per conto suo, così il telefono sa quando conviene aspettare invece di
  // leggere con l'AI locale solo le prime pagine di una circolare lunga:
  // - serverSummaries: c'è GEMINI_API_KEY, quindi il cron riassume le circolari da solo;
  // - serverWindowFrom: il cron recupera solo le circolari da questo numero in su;
  // - serverGaveUp: circolari su cui ha smesso di riprovare (MAX_ATTEMPTS tentativi falliti).
  const serverSummaries = !!c.env.GEMINI_API_KEY;
  let serverWindowFrom: number | null = null;
  let serverGaveUp: number[] = [];
  if (serverSummaries) {
    const windowRow = await c.env.DB.prepare(
      'SELECT MIN(number) AS first FROM (SELECT number FROM circulars ORDER BY number DESC LIMIT ?)'
    ).bind(BACKFILL_WINDOW).first<{ first: number | null }>();
    serverWindowFrom = windowRow?.first ?? null;
    const gaveUp = await c.env.DB.prepare(
      'SELECT circular_number FROM circular_ai_server_attempts WHERE attempts >= ?'
    ).bind(MAX_ATTEMPTS).all<{ circular_number: number }>();
    serverGaveUp = gaveUp.results.map((r) => r.circular_number);
  }

  return c.json({
    analyses: rows.results.map(analysisJson),
    serverSummaries,
    serverWindowFrom,
    serverGaveUp,
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
    `SELECT ${ANALYSIS_COLUMNS} FROM circular_ai_analysis WHERE circular_number = ?`
  ).bind(number).first<AnalysisRow>();

  if (!row) return c.json({ error: 'Nessuna analisi in cache per questa circolare' }, 404);

  return c.json(analysisJson(row));
});

// ---------------------------------------------------------------------------
// PUT /api/circulars/:number/analysis — Salva l'analisi AI in cache
// ---------------------------------------------------------------------------
// Chiunque sia autenticato può scrivere, ma solo con un livello uguale o superiore a quello già
// salvato: un riassunto dell'AI locale non sostituisce mai quello di Gemini, e il ripiego
// euristico non si salva affatto. Se il salvataggio viene rifiutato la risposta contiene
// l'analisi che resta valida (`stored: false, current`), così il telefono mostra quella.
// Non riceve mai il testo del PDF, solo l'esito.
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
  const modelLabel = body.modelLabel ?? 'Sconosciuto';
  const isFallback = !!body.isFallback;
  const tier = tierOf(isFallback, modelLabel);

  const stored = tier > 0 && await upsertAnalysis(
    c.env,
    number,
    {
      badge: body.badge,
      summary: body.summary,
      deadlines: body.deadlines ?? [],
      isFallback,
      modelLabel,
    },
    tier,
    payload.sub
  );

  if (stored) return c.json({ success: true, stored: true });

  const current = await c.env.DB.prepare(
    `SELECT ${ANALYSIS_COLUMNS} FROM circular_ai_analysis WHERE circular_number = ?`
  ).bind(number).first<AnalysisRow>();
  return c.json({ success: true, stored: false, current: current ? analysisJson(current) : null });
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
