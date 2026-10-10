// =============================================================================
// CIRCOLARE+ — Gita (/api/gita/*)
// =============================================================================
//
// Documenti e link della gita della classe. Lettura per tutta la classe; scrittura solo per il
// Rappresentante. Ogni modifica crea una versione nuova: quelle vecchie restano consultabili.
// Il file PDF e il testo estratto dal telefono del Rappresentante stanno su R2; il server non
// chiama nessun modello AI (le chiavi restano sul telefono di chi usa l'assistente).

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware, requireRole, newUUID, resolveClassId } from '../auth';
import { notifyUsers } from '../services/fcm';
import { inBackground } from '../services/background';

export const GITA_MAX_BYTES = 100 * 1024 * 1024;
const MAX_TITLE = 200;
const MAX_NOTE = 1000;
const MAX_REASON = 500;
/** Titoli di circolare che parlano di gita: minuscole, senza accenti (il confronto è LIKE). */
export const GITA_KEYWORDS = ['gita', 'uscita', 'viaggio', 'pullman', 'escursione'];
export const GITA_CATEGORIES = ['PROGRAMMA', 'PREVENTIVO', 'SCADENZA', 'REGOLAMENTO', 'PAGAMENTO', 'ALTRO'] as const;
type GitaCategory = (typeof GITA_CATEGORIES)[number];

interface ItemRow {
  id: string;
  class_id: string;
  kind: 'DOCUMENT' | 'LINK';
  category: string;
  title: string;
  created_by: string;
  created_at: string;
  withdrawn: number;
  withdrawn_at: string | null;
}

interface VersionRow {
  id: string;
  item_id: string;
  version_no: number;
  uploaded_by: string;
  uploaded_at: string;
  file_r2_key: string | null;
  file_mime: string | null;
  file_size: number | null;
  text_r2_key: string | null;
  text_chars: number;
  url: string | null;
  note: string | null;
}

interface ReportRow {
  id: string;
  class_id: string;
  item_id: string;
  version_id: string | null;
  reported_by: string;
  reason: string;
  status: 'OPEN' | 'RESOLVED';
  created_at: string;
  resolved_at: string | null;
  resolved_by: string | null;
}

type Ctx = { Bindings: Env; Variables: { jwtPayload: JWTPayload } };
const gita = new Hono<Ctx>();

gita.use('*', authMiddleware());

/**
 * Data e ora di Roma nel formato "AAAA-MM-GG HH:MM:SS". CURRENT_TIMESTAMP di SQLite è in UTC: un
 * caricamento dopo mezzanotte italiana risulterebbe del giorno prima.
 */
export function romeNow(): string {
  return new Date().toLocaleString('sv-SE', { timeZone: 'Europe/Rome' });
}

function clean(value: unknown, max: number): string | null {
  if (typeof value !== 'string') return null;
  const t = value.trim();
  return t.length > 0 && t.length <= max ? t : null;
}

function validHttpUrl(value: string): boolean {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' || u.protocol === 'http:';
  } catch {
    return false;
  }
}

function categoryOf(value: unknown): GitaCategory | null {
  return typeof value === 'string' && (GITA_CATEGORIES as readonly string[]).includes(value)
    ? (value as GitaCategory)
    : null;
}

function versionJson(v: VersionRow) {
  return {
    id: v.id,
    versionNo: v.version_no,
    uploadedBy: v.uploaded_by,
    uploadedAt: v.uploaded_at,
    hasFile: v.file_r2_key !== null,
    fileMime: v.file_mime,
    fileSize: v.file_size,
    textChars: v.text_chars,
    url: v.url,
    note: v.note,
  };
}

function itemJson(item: ItemRow, current: VersionRow | null) {
  return {
    id: item.id,
    kind: item.kind,
    category: item.category,
    title: item.title,
    createdBy: item.created_by,
    createdAt: item.created_at,
    withdrawn: item.withdrawn === 1,
    current: current ? versionJson(current) : null,
  };
}

function reportJson(r: ReportRow, itemTitle: string) {
  return {
    id: r.id,
    itemId: r.item_id,
    itemTitle,
    versionId: r.version_id,
    reportedBy: r.reported_by,
    reason: r.reason,
    status: r.status,
    createdAt: r.created_at,
    resolvedAt: r.resolved_at,
  };
}

/** Voce della classe (non ritirata oppure, se `withWithdrawn`, anche ritirata): 404 se altra classe. */
async function loadItem(
  c: { env: Env },
  classId: string,
  itemId: string,
  withWithdrawn = false
): Promise<ItemRow | null> {
  const row = await c.env.DB.prepare('SELECT * FROM gita_items WHERE id = ? AND class_id = ?')
    .bind(itemId, classId)
    .first<ItemRow>();
  if (!row) return null;
  if (row.withdrawn === 1 && !withWithdrawn) return null;
  return row;
}

async function currentVersion(env: Env, itemId: string): Promise<VersionRow | null> {
  return env.DB.prepare('SELECT * FROM gita_versions WHERE item_id = ? ORDER BY version_no DESC LIMIT 1')
    .bind(itemId)
    .first<VersionRow>();
}

async function nextVersionNo(env: Env, itemId: string): Promise<number> {
  const row = await env.DB.prepare('SELECT MAX(version_no) AS m FROM gita_versions WHERE item_id = ?')
    .bind(itemId)
    .first<{ m: number | null }>();
  return (row?.m ?? 0) + 1;
}

/** Testo estratto dal PDF sul telefono del Rappresentante, letto da R2 quando serve. */
async function readText(env: Env, key: string | null): Promise<string> {
  if (!key) return '';
  const object = await env.CIRCULARS_BUCKET.get(key);
  return object ? await object.text() : '';
}

/** Legge il corpo multipart di un documento: file PDF + testo estratto dal telefono. */
async function readDocumentForm(c: { req: { parseBody: () => Promise<Record<string, unknown>> } }) {
  const body = await c.req.parseBody();
  const file = body['file'];
  const text = typeof body['text'] === 'string' ? (body['text'] as string) : '';
  const note = typeof body['note'] === 'string' ? (body['note'] as string).trim().slice(0, MAX_NOTE) : null;
  if (!(file instanceof File)) return { error: 'Manca il file PDF' } as const;
  if (file.type !== 'application/pdf') return { error: 'Solo file PDF' } as const;
  if (file.size === 0) return { error: 'File vuoto' } as const;
  if (file.size > GITA_MAX_BYTES) return { error: 'File troppo grande (massimo 100 MB)' } as const;
  if (new TextEncoder().encode(text).byteLength > GITA_MAX_BYTES) {
    return { error: 'Testo estratto troppo grande (massimo 100 MB)' } as const;
  }
  return { file, text, note } as const;
}

async function storeVersion(
  env: Env,
  args: {
    classId: string;
    itemId: string;
    uploadedBy: string;
    file: File | null;
    text: string;
    url: string | null;
    note: string | null;
  }
): Promise<VersionRow> {
  const versionId = newUUID();
  const versionNo = await nextVersionNo(env, args.itemId);
  const base = `gita/${args.classId}/${args.itemId}/${versionId}`;
  let fileKey: string | null = null;
  if (args.file) {
    fileKey = `${base}.pdf`;
    await env.CIRCULARS_BUCKET.put(fileKey, args.file.stream(), {
      httpMetadata: { contentType: 'application/pdf' },
    });
  }
  let textKey: string | null = null;
  if (args.text.length > 0) {
    textKey = `${base}.txt`;
    await env.CIRCULARS_BUCKET.put(textKey, args.text, {
      httpMetadata: { contentType: 'text/plain; charset=utf-8' },
    });
  }
  await env.DB.prepare(
    `INSERT INTO gita_versions (id, item_id, version_no, uploaded_by, uploaded_at, file_r2_key, file_mime, file_size,
       text_r2_key, text_chars, url, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    versionId,
    args.itemId,
    versionNo,
    args.uploadedBy,
    romeNow(),
    fileKey,
    args.file ? 'application/pdf' : null,
    args.file ? args.file.size : null,
    textKey,
    args.text.length,
    args.url,
    args.note
  ).run();
  return (await env.DB.prepare('SELECT * FROM gita_versions WHERE id = ?').bind(versionId).first<VersionRow>())!;
}

async function classMemberIds(env: Env, classId: string, roles: string[]): Promise<string[]> {
  const placeholders = roles.map(() => '?').join(', ');
  const rows = await env.DB.prepare(
    `SELECT id FROM users WHERE class_id = ? AND role IN (${placeholders})`
  ).bind(classId, ...roles).all<{ id: string }>();
  return rows.results.map((r) => r.id);
}

// ---------------------------------------------------------------------------
// GET /api/gita — Elenco della gita: documenti e link correnti, più le segnalazioni visibili
// ---------------------------------------------------------------------------
gita.get('/', async (c) => {
  const classId = await resolveClassId(c);
  const payload = c.get('jwtPayload');
  const items = await c.env.DB.prepare(
    'SELECT * FROM gita_items WHERE class_id = ? AND withdrawn = 0 ORDER BY created_at DESC, id'
  ).bind(classId).all<ItemRow>();

  const out = [];
  for (const item of items.results) {
    out.push(itemJson(item, await currentVersion(c.env, item.id)));
  }

  // Circolari della gita: quelle che il Rappresentante ha aggiunto per la classe, più quelle il cui
  // titolo parla di gita o uscita (la parola chiave è in GITA_KEYWORDS).
  const circulars = await c.env.DB.prepare(
    `SELECT c.number, c.title, c.publish_date, g.circular_number IS NOT NULL AS pinned
     FROM circulars c LEFT JOIN gita_circulars g ON g.circular_number = c.number AND g.class_id = ?
     WHERE g.circular_number IS NOT NULL OR ${GITA_KEYWORDS.map(() => 'LOWER(c.title) LIKE ?').join(' OR ')}
     ORDER BY c.publish_date DESC, c.number DESC LIMIT 100`
  ).bind(classId, ...GITA_KEYWORDS.map((k) => `%${k}%`)).all<{ number: number; title: string; publish_date: string; pinned: number }>();

  // Il Rappresentante vede tutte le segnalazioni della classe; gli altri solo le proprie.
  const isRep = payload.role === 'REPRESENTATIVE';
  const reports = await c.env.DB.prepare(
    `SELECT r.*, i.title AS item_title FROM gita_reports r JOIN gita_items i ON i.id = r.item_id
     WHERE r.class_id = ? ${isRep ? '' : 'AND r.reported_by = ?'} ORDER BY r.created_at DESC`
  ).bind(...(isRep ? [classId] : [classId, payload.sub])).all<ReportRow & { item_title: string }>();

  return c.json({
    items: out,
    circulars: circulars.results.map((r) => ({
      number: r.number,
      title: r.title,
      publishDate: r.publish_date,
      pinned: r.pinned === 1,
    })),
    reports: reports.results.map((r) => reportJson(r, r.item_title)),
    canEdit: isRep,
  });
});

// ---------------------------------------------------------------------------
// GET /api/gita/items/:id/versions — Storico completo (anche per voci ritirate, solo Rappresentante)
// ---------------------------------------------------------------------------
gita.get('/items/:id/versions', async (c) => {
  const classId = await resolveClassId(c);
  const isRep = c.get('jwtPayload').role === 'REPRESENTATIVE';
  const item = await loadItem(c, classId, c.req.param('id') as string, isRep);
  if (!item) return c.json({ error: 'Voce non trovata' }, 404);
  const rows = await c.env.DB.prepare('SELECT * FROM gita_versions WHERE item_id = ? ORDER BY version_no DESC')
    .bind(item.id).all<VersionRow>();
  return c.json({ item: itemJson(item, null), versions: rows.results.map(versionJson) });
});

// ---------------------------------------------------------------------------
// GET /api/gita/versions/:id/file — PDF di una versione (autenticato: niente rotta pubblica)
// ---------------------------------------------------------------------------
gita.get('/versions/:id/file', async (c) => {
  const classId = await resolveClassId(c);
  const isRep = c.get('jwtPayload').role === 'REPRESENTATIVE';
  const version = await c.env.DB.prepare(
    'SELECT v.*, i.class_id AS class_id, i.withdrawn AS withdrawn FROM gita_versions v JOIN gita_items i ON i.id = v.item_id WHERE v.id = ?'
  ).bind(c.req.param('id')).first<VersionRow & { class_id: string; withdrawn: number }>();
  if (!version || version.class_id !== classId || (version.withdrawn === 1 && !isRep) || !version.file_r2_key) {
    return c.json({ error: 'File non trovato' }, 404);
  }
  const object = await c.env.CIRCULARS_BUCKET.get(version.file_r2_key);
  if (!object) return c.json({ error: 'File non trovato' }, 404);
  return new Response(object.body, {
    headers: {
      'Content-Type': 'application/pdf',
      'Cache-Control': 'private, max-age=3600',
      'X-Content-Type-Options': 'nosniff',
    },
  });
});

// ---------------------------------------------------------------------------
// GET /api/gita/corpus — Materiale per l'assistente: versione corrente di ogni voce, con testo
// ---------------------------------------------------------------------------
gita.get('/corpus', async (c) => {
  const classId = await resolveClassId(c);
  const items = await c.env.DB.prepare(
    'SELECT * FROM gita_items WHERE class_id = ? AND withdrawn = 0 ORDER BY created_at DESC'
  ).bind(classId).all<ItemRow>();
  const out = [];
  for (const item of items.results) {
    const v = await currentVersion(c.env, item.id);
    out.push({
      itemId: item.id,
      kind: item.kind,
      category: item.category,
      title: item.title,
      uploadedAt: v?.uploaded_at ?? item.created_at,
      versionNo: v?.version_no ?? null,
      url: v?.url ?? null,
      text: v ? await readText(c.env, v.text_r2_key) : '',
    });
  }
  return c.json({ documents: out });
});

// ---------------------------------------------------------------------------
// POST /api/gita/circulars — Aggiunge una circolare alla gita della classe. Solo Rappresentante
// ---------------------------------------------------------------------------
gita.post('/circulars', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const number = typeof body?.number === 'number' ? body.number : Number(body?.number);
  if (!Number.isInteger(number) || number <= 0) return c.json({ error: 'Numero di circolare non valido' }, 400);
  const exists = await c.env.DB.prepare('SELECT number FROM circulars WHERE number = ?').bind(number).first();
  if (!exists) return c.json({ error: 'Circolare non trovata' }, 404);
  await c.env.DB.prepare('INSERT OR IGNORE INTO gita_circulars (class_id, circular_number, added_by) VALUES (?, ?, ?)')
    .bind(classId, number, c.get('jwtPayload').sub).run();
  return c.json({ ok: true }, 201);
});

// ---------------------------------------------------------------------------
// DELETE /api/gita/circulars/:number — La toglie dalla gita della classe. Solo Rappresentante
// ---------------------------------------------------------------------------
gita.delete('/circulars/:number', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const number = Number(c.req.param('number'));
  if (!Number.isInteger(number)) return c.json({ error: 'Numero di circolare non valido' }, 400);
  await c.env.DB.prepare('DELETE FROM gita_circulars WHERE class_id = ? AND circular_number = ?')
    .bind(classId, number).run();
  return c.json({ ok: true });
});

// ---------------------------------------------------------------------------
// POST /api/gita/items — Nuovo documento (multipart) o nuovo link (JSON). Solo Rappresentante
// ---------------------------------------------------------------------------
gita.post('/items', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const me = c.get('jwtPayload').sub;
  const isMultipart = (c.req.header('content-type') ?? '').startsWith('multipart/form-data');

  if (isMultipart) {
    const form = await readDocumentForm(c);
    if ('error' in form) return c.json({ error: form.error }, 400);
    const body = await c.req.parseBody();
    const title = clean(body['title'], MAX_TITLE);
    const category = categoryOf(body['category']);
    if (!title) return c.json({ error: 'Titolo mancante' }, 400);
    if (!category) return c.json({ error: 'Categoria non valida' }, 400);
    const itemId = newUUID();
    await c.env.DB.prepare(
      'INSERT INTO gita_items (id, class_id, kind, category, title, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).bind(itemId, classId, 'DOCUMENT', category, title, me, romeNow()).run();
    const version = await storeVersion(c.env, {
      classId, itemId, uploadedBy: me, file: form.file, text: form.text, url: null, note: form.note,
    });
    return c.json({ id: itemId, version: versionJson(version) }, 201);
  }

  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const title = clean(body?.title, MAX_TITLE);
  const url = clean(body?.url, 2000);
  if (!title) return c.json({ error: 'Titolo mancante' }, 400);
  if (!url || !validHttpUrl(url)) return c.json({ error: 'Link non valido (serve http o https)' }, 400);
  const note = clean(body?.note, MAX_NOTE);
  const itemId = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO gita_items (id, class_id, kind, category, title, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(itemId, classId, 'LINK', 'ALTRO', title, me, romeNow()).run();
  const version = await storeVersion(c.env, {
    classId, itemId, uploadedBy: me, file: null, text: '', url, note,
  });
  return c.json({ id: itemId, version: versionJson(version) }, 201);
});

// ---------------------------------------------------------------------------
// POST /api/gita/items/:id/versions — Sostituisce un documento (multipart) o un link (JSON).
// La versione precedente resta nello storico. Solo Rappresentante.
// ---------------------------------------------------------------------------
gita.post('/items/:id/versions', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const me = c.get('jwtPayload').sub;
  const item = await loadItem(c, classId, c.req.param('id') as string);
  if (!item) return c.json({ error: 'Voce non trovata' }, 404);
  const isMultipart = (c.req.header('content-type') ?? '').startsWith('multipart/form-data');

  if (item.kind === 'DOCUMENT') {
    if (!isMultipart) return c.json({ error: 'Per un documento serve il file PDF' }, 400);
    const form = await readDocumentForm(c);
    if ('error' in form) return c.json({ error: form.error }, 400);
    const version = await storeVersion(c.env, {
      classId, itemId: item.id, uploadedBy: me, file: form.file, text: form.text, url: null, note: form.note,
    });
    return c.json({ version: versionJson(version) }, 201);
  }

  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const url = clean(body?.url, 2000);
  if (!url || !validHttpUrl(url)) return c.json({ error: 'Link non valido (serve http o https)' }, 400);
  const version = await storeVersion(c.env, {
    classId, itemId: item.id, uploadedBy: me, file: null, text: '', url, note: clean(body?.note, MAX_NOTE),
  });
  return c.json({ version: versionJson(version) }, 201);
});

// ---------------------------------------------------------------------------
// PATCH /api/gita/items/:id — Titolo e categoria (nessuna nuova versione). Solo Rappresentante
// ---------------------------------------------------------------------------
gita.patch('/items/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const item = await loadItem(c, classId, c.req.param('id') as string);
  if (!item) return c.json({ error: 'Voce non trovata' }, 404);
  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const title = body?.title === undefined ? item.title : clean(body.title, MAX_TITLE);
  const category = body?.category === undefined ? item.category : categoryOf(body.category);
  if (!title) return c.json({ error: 'Titolo non valido' }, 400);
  if (!category) return c.json({ error: 'Categoria non valida' }, 400);
  await c.env.DB.prepare('UPDATE gita_items SET title = ?, category = ? WHERE id = ?')
    .bind(title, category, item.id).run();
  return c.json({ ok: true });
});

// ---------------------------------------------------------------------------
// DELETE /api/gita/items/:id — Ritiro logico: la voce non si vede più, lo storico resta. Solo Rappresentante
// ---------------------------------------------------------------------------
gita.delete('/items/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const item = await loadItem(c, classId, c.req.param('id') as string);
  if (!item) return c.json({ error: 'Voce non trovata' }, 404);
  await c.env.DB.prepare("UPDATE gita_items SET withdrawn = 1, withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?")
    .bind(item.id).run();
  return c.json({ ok: true });
});

// ---------------------------------------------------------------------------
// POST /api/gita/items/:id/reports — Segnalazione di una voce (o di una sua versione), per tutti
// ---------------------------------------------------------------------------
gita.post('/items/:id/reports', async (c) => {
  const classId = await resolveClassId(c);
  const me = c.get('jwtPayload').sub;
  const item = await loadItem(c, classId, c.req.param('id') as string);
  if (!item) return c.json({ error: 'Voce non trovata' }, 404);
  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const reason = clean(body?.reason, MAX_REASON);
  if (!reason) return c.json({ error: 'Scrivi il motivo della segnalazione' }, 400);
  let versionId: string | null = null;
  if (typeof body?.versionId === 'string') {
    const v = await c.env.DB.prepare('SELECT id FROM gita_versions WHERE id = ? AND item_id = ?')
      .bind(body.versionId, item.id).first<{ id: string }>();
    if (!v) return c.json({ error: 'Versione non trovata' }, 404);
    versionId = v.id;
  }
  const id = newUUID();
  await c.env.DB.prepare(
    'INSERT INTO gita_reports (id, class_id, item_id, version_id, reported_by, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, classId, item.id, versionId, me, reason, romeNow()).run();

  const reps = await classMemberIds(c.env, classId, ['REPRESENTATIVE']);
  if (reps.length > 0) {
    inBackground(c, notifyUsers(c.env, reps, 'Gita: segnalazione', `«${item.title}» segnalato come non corretto`, { type: 'gita_report' }));
  }
  return c.json({ id, status: 'OPEN' }, 201);
});

// ---------------------------------------------------------------------------
// PATCH /api/gita/reports/:id — Stato della segnalazione (aperta / risolta). Solo Rappresentante.
// Il segnalante viene avvisato quando la sua segnalazione è risolta.
// ---------------------------------------------------------------------------
gita.patch('/reports/:id', requireRole('REPRESENTATIVE'), async (c) => {
  const classId = await resolveClassId(c);
  const me = c.get('jwtPayload').sub;
  const report = await c.env.DB.prepare(
    'SELECT r.*, i.title AS item_title FROM gita_reports r JOIN gita_items i ON i.id = r.item_id WHERE r.id = ? AND r.class_id = ?'
  ).bind(c.req.param('id'), classId).first<ReportRow & { item_title: string }>();
  if (!report) return c.json({ error: 'Segnalazione non trovata' }, 404);
  const body = await c.req.json<Record<string, unknown>>().catch(() => null);
  const status = body?.status;
  if (status !== 'OPEN' && status !== 'RESOLVED') return c.json({ error: 'Stato non valido' }, 400);
  const resolving = status === 'RESOLVED' && report.status !== 'RESOLVED';
  await c.env.DB.prepare(
    'UPDATE gita_reports SET status = ?, resolved_at = ?, resolved_by = ? WHERE id = ?'
  ).bind(status, status === 'RESOLVED' ? romeNow() : null, status === 'RESOLVED' ? me : null, report.id).run();
  if (resolving) {
    inBackground(c, notifyUsers(c.env, [report.reported_by], 'Gita: segnalazione risolta', `Abbiamo aggiornato «${report.item_title}»`, { type: 'gita_report' }));
  }
  return c.json({ ok: true, status });
});

export default gita;
