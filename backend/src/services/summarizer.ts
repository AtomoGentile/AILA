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
import { classIdForLabel, displayClassLabel, loadClasses, MISSING_CLASS_SQL, type ClassInfo, type ClassNote } from './classAnalysis';

const API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// Stessa scaletta dell'app (ClientSideAiClassifier.MODEL_LADDER): se un alias sparisce si prova il
// successivo invece di smettere di funzionare.
const MODEL_LADDER = ['gemini-flash-latest', 'gemini-flash-lite-latest', 'gemini-2.5-flash'];

// Una sola chiamata per circolare vale per tutte le classi registrate: il prompt le elenca e il
// modello risponde con un riassunto comune più badge e nota per ciascuna (vedi classAnalysis.ts).

/** Circolari riassunte al massimo per giro di cron (ogni 15 minuti). */
const MAX_PER_RUN = 3;
/** Dopo questi tentativi falliti una circolare si lascia ai telefoni. */
export const MAX_ATTEMPTS = 3;
/** Solo le circolari più recenti: quelle vecchie non le apre più nessuno. */
export const BACKFILL_WINDOW = 30;
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
  /** Id delle classi a cui vale; vuoto = tutte. */
  classes: string[];
}

interface Analysis {
  badge: string;
  summary: string;
  deadlines: Deadline[];
  modelLabel: string;
  /** Badge e nota per classe (id -> ...). */
  perClass: Record<string, ClassNote>;
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

function buildPrompt(number: number, title: string, attachmentLabels: string[], classes: ClassInfo[]): string {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const year = schoolYearStart(now);
  const attachmentsLine = attachmentLabels.length
    ? `Dopo il PDF della circolare ci sono ${attachmentLabels.length} allegati (${attachmentLabels.join(', ')}): considerali parte della circolare.`
    : '';
  const classList = classes.map((c) => displayClassLabel(c.label)).join(', ');
  return `Sei AILA Assistant, l'assistente scolastico dell'app "AILA".
Analizza la circolare scolastica ufficiale allegata per gli studenti di scuola superiore di queste classi dell'istituto: ${classList}.
Oggi e' ${today}. Anno scolastico ${year}/${(year + 1) % 100}: le date scritte senza anno appartengono a questo anno scolastico (da settembre a dicembre l'anno e' ${year}, da gennaio ad agosto e' ${year + 1}).

CIRCOLARE N. ${number}: ${title}
${attachmentsLine}

Rispondi rigorosamente in formato JSON con questa struttura, senza testo aggiuntivo:
{
  "badge": "RELEVANT" | "POTENTIAL" | "NOT_RELEVANT",
  "summary": "5-6 righe in italiano, valide per tutti",
  "classes": [
    { "class": "4^CSA", "badge": "RELEVANT" | "POTENTIAL" | "NOT_RELEVANT", "note": "1-3 frasi solo per questa classe, oppure stringa vuota" }
  ],
  "deadlines": [
    { "title": "Titolo scadenza", "dueDate": "YYYY-MM-DD", "time": "HH:MM oppure null", "category": "VERIFICA" | "INTERROGAZIONE" | "PAGAMENTO" | "USCITA_DIDATTICA" | "AVVISO" | "ALTRO", "classes": ["4^CSA"] }
  ]
}

Regole sui badge (quello generale vale per uno studente qualunque dell'istituto, quelli in
"classes" per uno studente di quella classe):
- RELEVANT (Ti riguarda): indicazioni dirette e vincolanti, uscite o pagamenti per la classe o l'intero istituto.
- POTENTIAL (Potenziale interesse): corsi facoltativi pomeridiani, borse di studio, gare, open day.
- NOT_RELEVANT (Non sembra riguardarti): circolari riservate ad altre classi specifiche, docenti o personale ATA.

Regole su "summary": deve avere 5-6 righe, non una o due frasi, ed e' letto da tutte le classi.
Riporta sempre, se presenti nel testo: il destinatario esatto, le date che valgono per tutti
(giorno e mese), nomi di persone o enti coinvolti (relatori, associazioni, uffici), e l'obiettivo
concreto della circolare (cosa deve fare lo studente, entro quando, con quali modalita'). Se la
circolare elenca giorni, orari o materie per tutti (per esempio sportelli per disciplina),
riportali. NON mettere nel riassunto i dettagli che valgono per una sola classe (la sua data,
il suo orario, la sua aula presi da un calendario o da una tabella per classe): vanno nella nota
di quella classe in "classes".

Regole su "classes": una voce per OGNI classe dell'elenco (${classList}), con "class" scritto
esattamente come nell'elenco. "note" contiene solo quello che la circolare dice per quella classe
in particolare (es. "Il Consiglio della 4^CSA e' mercoledi' 14 ottobre dalle 16:00 alle 16:45,
con i rappresentanti dalle 16:30."); stringa vuota se non dice nulla di specifico per lei.

Scrivi le classi attaccate, senza spazio dopo il simbolo: "4^CSA", "5^BIA" (non "4^ CSA").

Regole su "deadlines": solo date che lo studente deve segnare in agenda (consegne, pagamenti,
adesioni entro una data, uscite, incontri). Non inventare date e non mettere la data di
pubblicazione. "classes" = le classi dell'elenco a cui vale quella scadenza, [] se vale per tutti.
Se la circolare ha date diverse per classe (es. un calendario dei consigli di classe), crea una
scadenza per ciascuna classe dell'elenco che vi compare, con la sua data e solo quella classe in
"classes"; ignora le classi che non sono nell'elenco. Lascia l'elenco vuoto se non ci sono date.`;
}

/**
 * "4^ CSA" -> "4^CSA" (anche con °/ª): l'etichetta della classe si scrive attaccata. Il modello
 * tende a copiare lo spazio dalle circolari, quindi si normalizza comunque dopo la risposta.
 */
export function compactClassLabels(text: string): string {
  return text.replace(/(\d)\s*([\^°ª])\s+([A-Z]{1,4}\b)/g, '$1$2$3');
}

function isIsoDate(v: unknown): v is string {
  return typeof v === 'string' && /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(v);
}

function isClockTime(v: unknown): v is string {
  return typeof v === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(v);
}

/** Stesse regole di CircularClassificationPrompt.parse nell'app. `null` se il JSON non va bene. */
export function parseAnalysis(text: string, model: string, classes: ClassInfo[]): Analysis | null {
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
  const summary = typeof obj.summary === 'string' ? compactClassLabels(obj.summary.trim()) : '';
  if (!summary) return null;
  const deadlines: Deadline[] = [];
  if (Array.isArray(obj.deadlines)) {
    for (const item of obj.deadlines as Record<string, unknown>[]) {
      if (!item || typeof item !== 'object') continue;
      const title = typeof item.title === 'string' ? item.title.trim() : '';
      if (!title || !isIsoDate(item.dueDate)) continue;
      const category = typeof item.category === 'string' ? item.category.toUpperCase() : '';
      const labels = Array.isArray(item.classes) ? item.classes : [];
      const classIds = [...new Set(labels.map((l) => classIdForLabel(l, classes)).filter((id): id is string => id !== null))];
      // Una scadenza assegnata solo a classi che non sono registrate non riguarda nessuno qui.
      if (labels.length > 0 && classIds.length === 0) continue;
      deadlines.push({
        title: compactClassLabels(title).slice(0, 120),
        dueDate: item.dueDate,
        time: isClockTime(item.time) ? item.time : null,
        category: VALID_CATEGORIES.includes(category) ? category : 'ALTRO',
        classes: classIds,
      });
    }
  }
  const perClass: Record<string, ClassNote> = {};
  if (Array.isArray(obj.classes)) {
    for (const item of obj.classes as Record<string, unknown>[]) {
      if (!item || typeof item !== 'object') continue;
      const id = classIdForLabel(item.class, classes);
      if (!id) continue;
      const classBadge = typeof item.badge === 'string' ? item.badge.toUpperCase() : '';
      perClass[id] = {
        badge: VALID_BADGES.includes(classBadge) ? classBadge : badge,
        note: typeof item.note === 'string' ? compactClassLabels(item.note.trim()).slice(0, 600) : '',
      };
    }
  }
  // Ogni classe dell'elenco ha la sua voce, anche se il modello l'ha saltata: vuol dire che la
  // circolare non dice niente di specifico per lei. Senza, il recupero delle classi mancanti
  // (MISSING_CLASS_SQL) rifarebbe questa circolare a ogni giro.
  for (const c of classes) {
    if (!perClass[c.id]) perClass[c.id] = { badge, note: '' };
  }
  return { badge, summary, deadlines, modelLabel: `Google Gemini (${model})`, perClass };
}

async function readPdf(env: Env, key: string): Promise<Uint8Array | null> {
  const object = await env.CIRCULARS_BUCKET.get(key);
  if (!object) return null;
  return new Uint8Array(await object.arrayBuffer());
}

async function callGemini(apiKey: string, parts: unknown[], classes: ClassInfo[]): Promise<Outcome> {
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
      const analysis = parseAnalysis(text, model, classes);
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
  a: {
    badge: string;
    summary: string;
    deadlines: unknown[];
    isFallback: boolean;
    modelLabel: string;
    /** Solo dal riassunto del server; un'analisi fatta da un telefono lo lascia vuoto. */
    perClass?: Record<string, ClassNote> | null;
  },
  tier: number,
  submittedBy: string | null
): Promise<boolean> {
  const result = await env.DB.prepare(
    `INSERT INTO circular_ai_analysis
       (circular_number, badge, summary, deadlines_json, is_fallback, model_label, submitted_by, tier, per_class_json, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(circular_number) DO UPDATE SET
       per_class_json = excluded.per_class_json,
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
    tier,
    // Anche vuoto ('{}') per il server: segna che l'analisi per classe è già stata fatta, così
    // il recupero in summarizePendingCirculars non la rifà a ogni giro.
    a.perClass ? JSON.stringify(a.perClass) : null
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
    const classes = await loadClasses(env);
    parts.push({ text: buildPrompt(circ.number, circ.title, labels, classes) });

    const outcome = await callGemini(apiKey, parts, classes);
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
 * Le circolari più vecchie di BACKFILL_WINDOW il cron non le rifà: le rifà il server quando
 * qualcuno di una classe senza la sua parte ne apre una (GET /api/circulars/:number/analysis).
 * Una sola volta ogni ON_DEMAND_COOLDOWN_MINUTES per circolare, e mai oltre MAX_ATTEMPTS
 * fallimenti, così dieci compagni che aprono la stessa circolare non fanno dieci chiamate.
 * Ritorna `true` se questa richiesta si è presa il compito.
 */
const ON_DEMAND_COOLDOWN_MINUTES = 15;

export async function claimOnDemandSummary(env: Env, number: number): Promise<boolean> {
  if (!env.GEMINI_API_KEY) return false;
  const result = await env.DB.prepare(
    `INSERT INTO circular_ai_server_attempts (circular_number, attempts, last_attempt_at)
     VALUES (?, 0, CURRENT_TIMESTAMP)
     ON CONFLICT(circular_number) DO UPDATE SET last_attempt_at = CURRENT_TIMESTAMP
     WHERE circular_ai_server_attempts.attempts < ?
       AND circular_ai_server_attempts.last_attempt_at < datetime('now', ?)`
  ).bind(number, MAX_ATTEMPTS, `-${ON_DEMAND_COOLDOWN_MINUTES} minutes`).run();
  return (result.meta.changes ?? 0) > 0;
}

/** L'analisi di questa circolare manca della parte di qualche classe (o non è del server)? */
export async function needsServerSummary(env: Env, number: number): Promise<boolean> {
  if (!env.GEMINI_API_KEY) return false;
  const row = await env.DB.prepare(
    `SELECT 1 AS missing FROM circulars c
     LEFT JOIN circular_ai_analysis a ON a.circular_number = c.number
     WHERE c.number = ? AND (a.tier IS NULL OR a.tier < ? OR ${MISSING_CLASS_SQL})`
  ).bind(number, SERVER_ANALYSIS_TIER).first<{ missing: number }>();
  return !!row;
}

/**
 * Recupera le circolari recenti senza un riassunto di Gemini (nuove prima che la chiave fosse
 * impostata, o fallite per quota esaurita) o con un riassunto di prima dell'analisi per classe.
 * Al massimo MAX_PER_RUN per giro.
 */
export async function summarizePendingCirculars(env: Env): Promise<void> {
  if (!env.GEMINI_API_KEY) return;
  const rows = await env.DB.prepare(
    `SELECT c.number, c.title, c.r2_pdf_key, c.attachments_json
     FROM (SELECT * FROM circulars ORDER BY number DESC LIMIT ?) c
     LEFT JOIN circular_ai_analysis a ON a.circular_number = c.number
     LEFT JOIN circular_ai_server_attempts t ON t.circular_number = c.number
     WHERE (a.tier IS NULL OR a.tier < ?
            -- Manca la parte di una classe (iscritta dopo, o riassunto di prima dell'analisi
            -- per classe): si rifà, così ogni classe ha le sue specifiche.
            OR ${MISSING_CLASS_SQL})
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
