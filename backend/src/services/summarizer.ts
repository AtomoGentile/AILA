// =============================================================================
// AILA — Riassunto delle circolari fatto dal server con Google Gemini
// =============================================================================
//
// Prima ogni telefono riassumeva le circolari per conto suo, con la chiave personale o con l'AI
// locale: lento su molti telefoni, e chi arrivava dopo rifaceva lo stesso lavoro. Se il secret
// GEMINI_API_KEY è impostato, il cron riassume ogni circolare nuova appena la scarica (prima di
// mandare la notifica, così chi la apre trova già il riassunto) e recupera quelle rimaste indietro.
//
// Sicurezza della chiave:
// - è un secret di Cloudflare (`wrangler secret put GEMINI_API_KEY`), mai nel codice o nell'app;
// - viaggia solo nell'header `x-goog-api-key`, mai nell'URL (che può finire nei log);
// - nessuna rotta HTTP la usa: gira solo dal cron, sui PDF già in cache su R2, quindi nessun
//   utente può farle generare testo a piacere;
// - al massimo MAX_PER_RUN circolari per giro e MAX_ATTEMPTS tentativi per circolare.
//
// A Gemini si manda il PDF così com'è (inline, base64): legge anche tabelle e scansioni, e il
// Worker non deve estrarre testo.

import type { CircularAttachment, Env } from '../types';

const API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// Stessa scaletta dell'app (ClientSideAiClassifier.MODEL_LADDER): se un alias sparisce si prova il
// successivo invece di smettere di funzionare.
const MODEL_LADDER = ['gemini-flash-latest', 'gemini-flash-lite-latest', 'gemini-2.5-flash'];

// Stesso contesto di default dell'app (AiClassifier.classifyCircularText): l'analisi è condivisa
// fra tutti, quindi deve essere fatta con lo stesso contesto che userebbe un telefono.
const STUDENT_CONTEXT = 'Studente di scuola superiore, classe 4^ CSA';

/** Circolari riassunte al massimo per giro di cron (ogni 15 minuti). */
const MAX_PER_RUN = 3;
/** Dopo questi tentativi falliti una circolare si lascia ai telefoni. */
const MAX_ATTEMPTS = 3;
/** Solo le circolari più recenti: quelle vecchie non le apre più nessuno. */
const BACKFILL_WINDOW = 30;
/** Oltre questa dimensione totale (PDF + allegati) gli allegati restano fuori. Limite API: 20 MB. */
const MAX_INLINE_BYTES = 12 * 1024 * 1024;
const REQUEST_TIMEOUT_MS = 60_000;

export const SERVER_ANALYSIS_TIER = 2;

const VALID_BADGES = ['RELEVANT', 'POTENTIAL', 'NOT_RELEVANT'];
const VALID_CATEGORIES = ['VERIFICA', 'INTERROGAZIONE', 'PAGAMENTO', 'USCITA_DIDATTICA', 'AVVISO', 'ALTRO'];

interface Deadline {
  title: string;
  dueDate: string;
  time: string | null;
  category: string;
}

interface Analysis {
  badge: string;
  summary: string;
  deadlines: Deadline[];
  modelLabel: string;
}

type Outcome =
  | { ok: true; analysis: Analysis }
  | { ok: false; reason: string; retryLater: boolean };

function toBase64(bytes: Uint8Array): string {
  // Uint8Array.toBase64 è nativo nei runtime recenti; il ripiego a blocchi evita di costruire una
  // stringa enorme con un solo String.fromCharCode(...bytes), che andrebbe in stack overflow.
  const native = (bytes as unknown as { toBase64?: () => string }).toBase64;
  if (typeof native === 'function') return native.call(bytes);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function schoolYearStart(now: Date): number {
  return now.getUTCMonth() >= 8 ? now.getUTCFullYear() : now.getUTCFullYear() - 1;
}

function buildPrompt(number: number, title: string, attachmentLabels: string[]): string {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const year = schoolYearStart(now);
  const attachmentsLine = attachmentLabels.length
    ? `Dopo il PDF della circolare ci sono ${attachmentLabels.length} allegati (${attachmentLabels.join(', ')}): considerali parte della circolare.`
    : '';
  return `Sei AILA Assistant, l'assistente scolastico dell'app "AILA".
Analizza la circolare scolastica ufficiale allegata per determinare se e quanto riguarda il seguente studente: "${STUDENT_CONTEXT}".
Oggi e' ${today}. Anno scolastico ${year}/${(year + 1) % 100}: le date scritte senza anno appartengono a questo anno scolastico (da settembre a dicembre l'anno e' ${year}, da gennaio ad agosto e' ${year + 1}).

CIRCOLARE N. ${number}: ${title}
${attachmentsLine}

Rispondi rigorosamente in formato JSON con questa struttura, senza testo aggiuntivo:
{
  "badge": "RELEVANT" | "POTENTIAL" | "NOT_RELEVANT",
  "summary": "5-6 righe in italiano",
  "deadlines": [
    { "title": "Titolo scadenza", "dueDate": "YYYY-MM-DD", "time": "HH:MM oppure null", "category": "VERIFICA" | "INTERROGAZIONE" | "PAGAMENTO" | "USCITA_DIDATTICA" | "AVVISO" | "ALTRO" }
  ]
}

Regole sui badge:
- RELEVANT (Ti riguarda): indicazioni dirette e vincolanti, uscite o pagamenti per la classe o l'intero istituto.
- POTENTIAL (Potenziale interesse): corsi facoltativi pomeridiani, borse di studio, gare, open day.
- NOT_RELEVANT (Non sembra riguardarti): circolari riservate ad altre classi specifiche, docenti o personale ATA.

Regole su "summary": deve avere 5-6 righe, non una o due frasi. Riporta sempre, se presenti nel
testo: il destinatario esatto, tutte le date citate (giorno e mese), nomi di persone o enti
coinvolti (relatori, associazioni, uffici), e l'obiettivo concreto della circolare (cosa deve fare
lo studente, entro quando, con quali modalita'). Se la circolare elenca giorni, orari o materie
(per esempio sportelli per disciplina), riportali. Non generalizzare se il documento contiene
questi dettagli: riportali per esteso invece di ometterli.

Regole su "deadlines": solo date che lo studente deve segnare in agenda (consegne, pagamenti,
adesioni entro una data, uscite, incontri). Non inventare date e non mettere la data di
pubblicazione. Lascia l'elenco vuoto se non ce ne sono.`;
}

function isIsoDate(v: unknown): v is string {
  return typeof v === 'string' && /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(v);
}

function isClockTime(v: unknown): v is string {
  return typeof v === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(v);
}

/** Stesse regole di CircularClassificationPrompt.parse nell'app. `null` se il JSON non va bene. */
function parseAnalysis(text: string, model: string): Analysis | null {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
  const badge = typeof obj.badge === 'string' ? obj.badge.toUpperCase() : '';
  if (!VALID_BADGES.includes(badge)) return null;
  const summary = typeof obj.summary === 'string' ? obj.summary.trim() : '';
  if (!summary) return null;
  const deadlines: Deadline[] = [];
  if (Array.isArray(obj.deadlines)) {
    for (const item of obj.deadlines as Record<string, unknown>[]) {
      if (!item || typeof item !== 'object') continue;
      const title = typeof item.title === 'string' ? item.title.trim() : '';
      if (!title || !isIsoDate(item.dueDate)) continue;
      const category = typeof item.category === 'string' ? item.category.toUpperCase() : '';
      deadlines.push({
        title: title.slice(0, 120),
        dueDate: item.dueDate,
        time: isClockTime(item.time) ? item.time : null,
        category: VALID_CATEGORIES.includes(category) ? category : 'ALTRO',
      });
    }
  }
  return { badge, summary, deadlines, modelLabel: `Google Gemini (${model})` };
}

async function readPdf(env: Env, key: string): Promise<Uint8Array | null> {
  const object = await env.CIRCULARS_BUCKET.get(key);
  if (!object) return null;
  return new Uint8Array(await object.arrayBuffer());
}

async function callGemini(apiKey: string, parts: unknown[]): Promise<Outcome> {
  let lastReason = 'nessun modello disponibile';
  for (const model of MODEL_LADDER) {
    let res: Response;
    try {
      res = await fetch(`${API_BASE}/${model}:generateContent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({
          contents: [{ role: 'user', parts }],
          generationConfig: { temperature: 0.2, responseMimeType: 'application/json' },
        }),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
    } catch (err) {
      return { ok: false, reason: `rete: ${(err as Error).message}`, retryLater: true };
    }
    if (res.ok) {
      const body = await res.json<{
        candidates?: { content?: { parts?: { text?: string }[] } }[];
      }>();
      const text = (body.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? '').join('');
      const analysis = parseAnalysis(text, model);
      if (analysis) return { ok: true, analysis };
      return { ok: false, reason: `risposta non valida da ${model}`, retryLater: false };
    }
    // Il corpo dell'errore non contiene la chiave; si tiene corto comunque.
    const errBody = (await res.text().catch(() => '')).slice(0, 200);
    lastReason = `HTTP ${res.status} da ${model}: ${errBody}`;
    // Quota esaurita o servizio giù: si riprova al giro dopo senza contare un tentativo.
    if (res.status === 429 || res.status >= 500) return { ok: false, reason: lastReason, retryLater: true };
    // Solo un modello inesistente fa provare il successivo.
    if (res.status !== 404) return { ok: false, reason: lastReason, retryLater: false };
  }
  return { ok: false, reason: lastReason, retryLater: false };
}

async function recordFailure(env: Env, number: number, reason: string): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO circular_ai_server_attempts (circular_number, attempts, last_error, last_attempt_at)
     VALUES (?, 1, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(circular_number) DO UPDATE SET
       attempts = attempts + 1, last_error = excluded.last_error, last_attempt_at = CURRENT_TIMESTAMP`
  ).bind(number, reason.slice(0, 300)).run();
}

/** Salva un'analisi rispettando il livello: sovrascrive solo un livello uguale o inferiore. */
export async function upsertAnalysis(
  env: Env,
  number: number,
  a: { badge: string; summary: string; deadlines: unknown[]; isFallback: boolean; modelLabel: string },
  tier: number,
  submittedBy: string | null
): Promise<boolean> {
  const result = await env.DB.prepare(
    `INSERT INTO circular_ai_analysis
       (circular_number, badge, summary, deadlines_json, is_fallback, model_label, submitted_by, tier, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(circular_number) DO UPDATE SET
       badge = excluded.badge,
       summary = excluded.summary,
       deadlines_json = excluded.deadlines_json,
       is_fallback = excluded.is_fallback,
       model_label = excluded.model_label,
       submitted_by = excluded.submitted_by,
       tier = excluded.tier,
       updated_at = CURRENT_TIMESTAMP
     WHERE excluded.tier >= circular_ai_analysis.tier`
  ).bind(
    number,
    a.badge,
    a.summary,
    JSON.stringify(a.deadlines ?? []),
    a.isFallback ? 1 : 0,
    a.modelLabel,
    submittedBy,
    tier
  ).run();
  return (result.meta.changes ?? 0) > 0;
}

/**
 * Riassume una circolare e salva il risultato. Ritorna `true` se il riassunto è stato salvato.
 * Non lancia mai: un errore qui non deve fermare la sincronizzazione delle circolari.
 */
export async function summarizeCircular(
  env: Env,
  circ: { number: number; title: string; r2_pdf_key: string; attachments_json: string }
): Promise<boolean> {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) return false;
  try {
    const pdf = await readPdf(env, circ.r2_pdf_key);
    if (!pdf) {
      await recordFailure(env, circ.number, 'PDF assente su R2');
      return false;
    }
    const parts: unknown[] = [{ inline_data: { mime_type: 'application/pdf', data: toBase64(pdf) } }];
    let total = pdf.byteLength;
    const labels: string[] = [];
    let attachments: CircularAttachment[] = [];
    try {
      attachments = JSON.parse(circ.attachments_json || '[]');
    } catch {
      attachments = [];
    }
    for (const att of attachments) {
      if (!att.pdfKey) continue;
      const bytes = await readPdf(env, att.pdfKey);
      if (!bytes || total + bytes.byteLength > MAX_INLINE_BYTES) continue;
      total += bytes.byteLength;
      labels.push(att.label);
      parts.push({ inline_data: { mime_type: 'application/pdf', data: toBase64(bytes) } });
    }
    parts.push({ text: buildPrompt(circ.number, circ.title, labels) });

    const outcome = await callGemini(apiKey, parts);
    if (!outcome.ok) {
      console.warn(`[Summarizer] Circolare ${circ.number}: ${outcome.reason}`);
      if (!outcome.retryLater) await recordFailure(env, circ.number, outcome.reason);
      return false;
    }
    await upsertAnalysis(
      env,
      circ.number,
      { ...outcome.analysis, isFallback: false },
      SERVER_ANALYSIS_TIER,
      null
    );
    console.log(`[Summarizer] Circolare ${circ.number} riassunta (${outcome.analysis.modelLabel})`);
    return true;
  } catch (err) {
    console.error(`[Summarizer] Circolare ${circ.number}: errore`, err);
    await recordFailure(env, circ.number, `eccezione: ${(err as Error).message}`).catch(() => {});
    return false;
  }
}

/**
 * Recupera le circolari recenti senza un riassunto di Gemini (nuove prima che la chiave fosse
 * impostata, o fallite per quota esaurita). Al massimo MAX_PER_RUN per giro.
 */
export async function summarizePendingCirculars(env: Env): Promise<void> {
  if (!env.GEMINI_API_KEY) return;
  const rows = await env.DB.prepare(
    `SELECT c.number, c.title, c.r2_pdf_key, c.attachments_json
     FROM (SELECT * FROM circulars ORDER BY number DESC LIMIT ?) c
     LEFT JOIN circular_ai_analysis a ON a.circular_number = c.number
     LEFT JOIN circular_ai_server_attempts t ON t.circular_number = c.number
     WHERE (a.tier IS NULL OR a.tier < ?)
       AND (t.attempts IS NULL OR t.attempts < ?)
     ORDER BY c.number DESC
     LIMIT ?`
  ).bind(BACKFILL_WINDOW, SERVER_ANALYSIS_TIER, MAX_ATTEMPTS, MAX_PER_RUN).all<{
    number: number;
    title: string;
    r2_pdf_key: string;
    attachments_json: string;
  }>();
  for (const row of rows.results) {
    await summarizeCircular(env, row);
  }
}
