// Porting della parte Google AI Studio di ClientSideAiClassifier.kt.
// La chiamata parte dal browser con la chiave personale dell'utente: il testo non passa dal
// server di AILA. generativelanguage.googleapis.com accetta il CORS (preflight verificato).
import type { DeadlineDto, RelevanceBadge } from '@worker/contracts';
import { classifyHeuristic } from './heuristic';
import { extractJsonObject } from './jsonExtractor';
import { truncatePdfTextForAi } from './text';
import type { Classification } from './types';

const API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';
const DEFAULT_MODEL = 'gemini-flash-latest';
const MODEL_LADDER = [
  'gemini-flash-latest',
  'gemini-flash-lite-latest',
  'gemini-2.5-flash',
  'gemini-2.5-flash-lite',
  'gemini-2.5-pro',
];
const MAX_PDF_CHARS = 24_000;
const REQUEST_TIMEOUT_MS = 120_000;
const MAX_RETRIES = 3;
const BADGES: RelevanceBadge[] = ['RELEVANT', 'POTENTIAL', 'NOT_RELEVANT'];

// Modello che ha risposto in questa sessione: le circolari dopo partono da lui.
let resolvedModel: string | null = null;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function candidates(): string[] {
  return [...new Set([...(resolvedModel ? [resolvedModel] : []), DEFAULT_MODEL, ...MODEL_LADDER])];
}

// Una generateContent, con i tentativi su 429 e 5xx (503 escluso: lo gestisce la scaletta).
async function postGenerate(apiKey: string, model: string, prompt: string): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(`${API_BASE}/${model}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.2, responseMimeType: 'application/json' },
      }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    const retriable = res.status === 429 || (res.status >= 500 && res.status !== 503);
    if (!retriable || attempt >= MAX_RETRIES) return res;
    await sleep(Math.min(1000 * 2 ** attempt, 20_000));
  }
}

// true quando conviene provare il modello dopo (ritirato o sovraccarico).
export function isModelUnavailable(status: number, body: string): boolean {
  if (status === 404 || status === 503) return true;
  const lower = body.toLowerCase();
  return ['not found', 'is not supported', 'does not exist', 'has been deprecated', 'unavailable', 'overloaded', 'high demand']
    .some((s) => lower.includes(s));
}

// Chiede a Google quali modelli vanno con questa chiave; preferisce un flash stabile.
async function discoverUsableModel(apiKey: string): Promise<string | null> {
  try {
    const res = await fetch(`${API_BASE}?pageSize=200`, { headers: { 'x-goog-api-key': apiKey } });
    if (!res.ok) return null;
    const data = (await res.json()) as { models?: { name?: string; supportedGenerationMethods?: string[] }[] };
    const models = (data.models ?? [])
      .filter((m) => m.name && m.supportedGenerationMethods?.includes('generateContent'))
      .map((m) => m.name!.replace(/^models\//, ''))
      .filter((n) => !['embedding', 'vision', 'tts', 'live', 'image', 'aqa'].some((x) => n.toLowerCase().includes(x)));
    return (
      models.find((n) => n.includes('flash') && !n.includes('preview')) ??
      models.find((n) => !n.includes('preview')) ??
      models[0] ??
      null
    );
  } catch {
    return null;
  }
}

export function buildPrompt(number: number, title: string, pdfText: string, studentContext: string): string {
  const truncated = truncatePdfTextForAi(pdfText, MAX_PDF_CHARS);
  return `Sei AILA Assistant, l'assistente scolastico dell'app "AILA".
Analizza il seguente testo estratto da una circolare scolastica ufficiale per determinare se e quanto riguarda il seguente studente: "${studentContext}".

CIRCOLARE N. ${number}: ${title}
TESTO DOCUMENTO:
${truncated}

Rispondi rigorosamente in formato JSON con questa struttura, senza testo aggiuntivo:
{
  "badge": "RELEVANT" | "POTENTIAL" | "NOT_RELEVANT",
  "summary": "5-6 righe in italiano",
  "deadlines": [
    {
      "title": "Titolo scadenza",
      "dueDate": "YYYY-MM-DD",
      "time": "HH:MM oppure null",
      "category": "PAGAMENTO" | "USCITA_DIDATTICA" | "AVVISO"
    }
  ]
}

Regole sui badge:
- RELEVANT (Ti riguarda): indicazioni dirette e vincolanti, uscite o pagamenti per la classe o l'intero istituto.
- POTENTIAL (Potenziale interesse): corsi facoltativi pomeridiani, borse di studio, gare, open day.
- NOT_RELEVANT (Non sembra riguardarti): circolari riservate ad altre classi specifiche, docenti o personale ATA.

Regole su "summary": deve avere 5-6 righe, non una o due frasi. Riporta sempre, se
presenti nel testo: il destinatario esatto, tutte le date citate (giorno e mese),
nomi di persone o enti coinvolti (relatori, associazioni, uffici), e l'obiettivo
concreto della circolare (cosa deve fare lo studente, entro quando, con quali
modalita'). Non generalizzare se il documento contiene questi dettagli: riportali
per esteso invece di ometterli.`;
}

type ParseResult = { ok: true; value: Classification } | { ok: false; reason: string };

/** Legge badge, riassunto e scadenze dalla risposta generateContent. */
export function parseGeminiResponse(number: number, raw: string, model: string): ParseResult {
  let root: { candidates?: { content?: { parts?: { text?: string }[] } }[] };
  try {
    root = JSON.parse(raw);
  } catch {
    return { ok: false, reason: `risposta non JSON valida da Google AI Studio: ${raw.slice(0, 200)}` };
  }
  const text = root.candidates?.[0]?.content?.parts?.[0]?.text;
  if (text == null) return { ok: false, reason: `risposta di Google AI Studio senza contenuto utilizzabile: ${raw.slice(0, 200)}` };

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(text);
  } catch {
    // Seconda possibilità: JSON sporcato (```json, virgolette non scappate).
    const repaired = extractJsonObject(text);
    try {
      if (!repaired) throw new Error();
      parsed = JSON.parse(repaired);
    } catch {
      return { ok: false, reason: `il modello non ha risposto in JSON come richiesto: ${text.slice(0, 200)}` };
    }
  }

  const rawBadge = parsed.badge;
  if (typeof rawBadge !== 'string' || !BADGES.includes(rawBadge as RelevanceBadge)) {
    return { ok: false, reason: `Google AI Studio ha risposto con un badge non valido ("${rawBadge ?? 'assente'}"): ${text.slice(0, 200)}` };
  }
  const summary = typeof parsed.summary === 'string' ? parsed.summary : 'Analisi AI disponibile senza riassunto dettagliato.';
  const deadlines: DeadlineDto[] = (Array.isArray(parsed.deadlines) ? parsed.deadlines : [])
    .filter((d): d is Record<string, unknown> => !!d && typeof d === 'object')
    .filter((d) => typeof d.title === 'string' && typeof d.dueDate === 'string')
    .map((d) => ({
      title: d.title as string,
      dueDate: d.dueDate as string,
      time: typeof d.time === 'string' && d.time !== 'null' ? d.time : null,
      category: typeof d.category === 'string' ? d.category : 'AVVISO',
    }));

  return {
    ok: true,
    value: { circularNumber: number, badge: rawBadge as RelevanceBadge, summary, deadlines, isFallback: false, modelLabel: `Google Gemini (${model})` },
  };
}

/** Classifica una circolare; se la chiave manca o Google fallisce, ripiego euristico con il motivo. */
export async function classifyCircular(
  apiKey: string,
  number: number,
  title: string,
  pdfText: string,
  studentContext: string
): Promise<Classification> {
  if (!apiKey.trim()) return classifyHeuristic(number, title, pdfText, null);

  try {
    const prompt = buildPrompt(number, title, pdfText, studentContext);
    let lastFailure = 'nessun modello disponibile';
    const tryModel = async (model: string, label = ''): Promise<ParseResult | null> => {
      const res = await postGenerate(apiKey, model, prompt);
      const body = await res.text();
      if (res.ok) {
        resolvedModel = model;
        return parseGeminiResponse(number, body, model);
      }
      lastFailure = `HTTP ${res.status} con il modello ${model}${label}: ${body.slice(0, 200)}`;
      return isModelUnavailable(res.status, body) ? null : { ok: false, reason: lastFailure };
    };

    for (const model of candidates()) {
      const r = await tryModel(model);
      if (r) return r.ok ? r.value : classifyHeuristic(number, title, pdfText, r.reason);
    }
    const discovered = await discoverUsableModel(apiKey);
    if (discovered) {
      const r = await tryModel(discovered, ' (rilevato automaticamente)');
      if (r?.ok) return r.value;
      if (r) lastFailure = r.reason;
    }
    return classifyHeuristic(number, title, pdfText, lastFailure);
  } catch (e) {
    const err = e as Error;
    return classifyHeuristic(number, title, pdfText, `eccezione ${err.name}: ${err.message || 'nessun dettaglio'}`);
  }
}

/** Pulsante "Prova la chiave": riporta la risposta di Google così com'è. */
export async function testKey(apiKey: string): Promise<string> {
  if (!apiKey.trim()) return 'Nessuna chiave inserita.';
  let lastFailure: string | null = null;
  for (const model of candidates()) {
    let res: Response;
    try {
      res = await postGenerate(apiKey, model, 'Rispondi solo con: ok');
    } catch (e) {
      return `Non sono riuscito a raggiungere Google: ${(e as Error).message}. Controlla la connessione.`;
    }
    if (res.ok) {
      resolvedModel = model;
      return `Chiave valida. Modello in uso: ${model}.`;
    }
    const body = await res.text();
    lastFailure = `HTTP ${res.status} (${model}): ${body.slice(0, 240)}`;
    if (!isModelUnavailable(res.status, body)) return `La chiave è stata rifiutata. Risposta di Google: ${lastFailure}`;
  }
  const discovered = await discoverUsableModel(apiKey);
  if (discovered) return `Chiave valida, ma nessuno dei modelli previsti è disponibile. Ne userò uno rilevato automaticamente: ${discovered}.`;
  return `Nessun modello utilizzabile con questa chiave. Ultimo errore: ${lastFailure ?? 'sconosciuto'}`;
}
