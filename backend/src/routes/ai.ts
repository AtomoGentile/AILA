// =============================================================================
// CIRCOLARE+ — Ai Routes (il chatbot di AILA Assistant, incluso per tutti)
// /api/ai/chat   — proxy autenticato verso Workers AI con Gemma 4 26B A4B
// /api/ai/status — servizio attivo? (nessuna generazione: non consuma neuron)
// =============================================================================

// Dalla v1.5.0 la chat dell'assistente non chiede piu' una chiave a chi la usa: passa dal
// Worker di AILA, che gira la richiesta su Workers AI (binding `AI`, vedi types.ts) con
// Gemma 4 26B A4B — il MoE di Google con 26B di parametri e 4B attivi per passata:
// https://developers.cloudflare.com/workers-ai/models/gemma-4-26b-a4b-it/
//
// La quota vale per tutti e sta tutta qui: sul piano Workers Free Cloudflare concede
// 10.000 neuron al giorno per l'intero account e li conta da sola, con precisione
// esatta ("non deve andare a stima, deve essere preciso"): quando finiscono blocca la
// richiesta con l'errore documentato 4006 (Workers AI "Errors" -> "Account limited",
// HTTP 429). La rotta non tiene contatori in D1 e non stima nulla: si fida di quel blocco
// e lo traduce in una risposta 429 pulita, che il client usa per passare alla riserva
// (chiave personale / AI locale, vedi AilaCloudClassifier).
//
// Perimetro: solo la chat e la stesura di eventi calendario. La lettura delle circolari
// resta dov'era — riassunti del server dalla cron con la chiave dedicata GEMINI_API_KEY
// (services/summarizer.ts), mai toccata da questa rotta. La rotta e' affidabile solo a
// utenti autenticati (authMiddleware), perche' la chat e' il servizio piu' esposto:
// senza autenticazione chiunque potrebbe bruciare la quota di tutta la scuola.

import { Hono } from 'hono';
import type { Env, JWTPayload } from '../types';
import { authMiddleware } from '../auth';

const MODEL = '@cf/google/gemma-4-26b-a4b-it';

// Stesso tetto in caratteri del client storico (ClientSideAiClassifier.MAX_PROMPT_CHARS):
// il limite lo fa rispettare qui, e non solo nell'app (che comunque fidarla poco): lo stesso
// endpoint puo' essere chiamato da chiunque abbia un account. Non e' una limitazione tecnica
// della finestra di Gemma (da centinaia di migliaia di token): e' prudenza, per non far
// bruciare in una sola richiesta i neuron del giorno a tutta la scuola.
const MAX_TOTAL_CHARS = 60_000;
const MAX_MESSAGES = 32;
const MIN_COMPLETION_TOKENS = 64;
const MAX_COMPLETION_TOKENS = 2048;

interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

/** Workers AI restituisce i modelli chat come Chat Completion; `response` copre il formato legacy. */
interface WorkersAiChatResult {
  choices?: Array<{ message?: { content?: string | null } }>;
  response?: string;
}

/**
 * `true` quando l'errore e' il limite giornaliero dei neuron. Rilevato dall'errore reale:
 * con il binding arriva come `AiError: 4006: you have used up your daily free allocation
 * of 10,000 neurons ...` (HTTP 429, codice interno 4006). Si riconosce sia dal `code`
 * sia dal testo del messaggio: se Cloudflare cambiasse la forma del codice, il testo
 * resta l'ultimo dato. Non entra in questo ramo per un errore qualsiasi: ogni altra
 * eccezione e' un errore del servizio, non fine quota.
 */
function isDailyQuotaError(err: unknown): boolean {
  const e = err as { code?: unknown; message?: unknown } | null;
  if (e && typeof e === 'object') {
    if (e.code === 4006 || e.code === '4006') return true;
    if (typeof e.message === 'string' && /4006|10,000 neurons|daily free allocation/.test(e.message)) return true;
  }
  const text = err instanceof Error ? err.message : String(err);
  return /4006|10,000 neurons|daily free allocation/.test(text);
}

/** Il messaggio esatto di errore, per riconoscerlo eventualmente nei 4006 testuali. */
function messageOf(err: unknown): string {
  if (err instanceof Error) return err.message;
  if (err && typeof err === 'object' && 'message' in err) return String(err.message);
  return String(err);
}

const ai = new Hono<{ Bindings: Env; Variables: { jwtPayload: JWTPayload } }>();

ai.use('*', authMiddleware());

// ---------------------------------------------------------------------------
// GET /api/ai/status — il servizio e' attivo? Nessuna generazione, quindi nessun neuron
// speso: serve a Impostazioni e alle prove di configurazione, dove bruciare neuron solo
// per dire "funziona" e' il contrario dell'aiuto che il pannello deve dare.
// ---------------------------------------------------------------------------
ai.get('/status', (c) => {
  if (!c.env.AI) {
    return c.json({ configured: false, error: 'Il binding AI non e\' impostato nel wrangler.toml del Worker' });
  }
  return c.json({ configured: true, model: MODEL });
});

// ---------------------------------------------------------------------------
// POST /api/ai/chat — una generazione per la chat (o per la stesura di un evento).
//
// Il client costruisce il prompt come da sempre (contesto app, i due round dell'assistente,
// prompt JSON per gli eventi): qui si fa solo da valvola — autenticazione, limiti di
// forma, traduzione dell'errore di quota. Nessun stato: l'assistente resta senza
// memoria server-side come prima, la conversazione vive sul dispositivo.
// ---------------------------------------------------------------------------
ai.post('/chat', async (c) => {
  if (!c.env.AI) {
    return c.json({
      error: 'Il servizio AI di AILA non e\' attivo: al Worker manca il binding [ai] di wrangler.toml',
      reason: 'SERVICE_NOT_CONFIGURED',
    }, 503);
  }

  let body: { messages?: unknown; json?: unknown; thinking?: unknown; maxTokens?: unknown };
  try {
    body = await c.req.json();
  } catch {
    return c.json({ error: 'Corpo della richiesta non valido (atteso JSON)' }, 400);
  }

  const rawMessages = Array.isArray(body?.messages) ? body.messages : null;
  if (!rawMessages || rawMessages.length === 0) {
    return c.json({ error: 'Manca la lista messages' }, 400);
  }
  if (rawMessages.length > MAX_MESSAGES) {
    return c.json({ error: `Troppi messaggi (${rawMessages.length} > ${MAX_MESSAGES})` }, 400);
  }

  // Solo testo: Gemma supporterebbe immagini e file inline, ma l'assistente non li usa
  // e i neuron gratis al giorno sono pochi — meglio che una richiesta assurda non
  // li porti via a tutti.
  const messages: ChatMessage[] = [];
  let totalChars = 0;
  for (const item of rawMessages) {
    const role = typeof item?.role === 'string' ? item.role : '';
    const content = typeof item?.content === 'string' ? item.content : '';
    if (role !== 'system' && role !== 'user' && role !== 'assistant') {
      return c.json({ error: 'role deve essere system, user o assistant' }, 400);
    }
    if (content.length === 0) {
      return c.json({ error: 'content vuoto in un messaggio' }, 400);
    }
    totalChars += content.length;
    messages.push({ role: role as ChatMessage['role'], content });
  }
  if (totalChars > MAX_TOTAL_CHARS) {
    return c.json({ error: `Prompt troppo lungo (${totalChars} > ${MAX_TOTAL_CHARS} caratteri)` }, 413);
  }

  const requested = typeof body.maxTokens === 'number' && Number.isFinite(body.maxTokens)
    ? Math.floor(body.maxTokens)
    : MAX_COMPLETION_TOKENS;
  const maxTokens = Math.min(Math.max(requested, MIN_COMPLETION_TOKENS), MAX_COMPLETION_TOKENS);
  // Gemma 4 "ragiona" di default (enable_thinking: true). In chat, come col thinkingBudget
  // = 0 di Gemini in ClientSideAiClassifier.postChat, il ragionamento aggiunge secondi e
  // neuron quasi senza cambiare le risposte: spento, a meno che il client non lo chieda
  // (l'interruttore "Ragionamento assistente" in Impostazioni, come accade sui modelli
  // locali). clear_thinking: il ragionamento resta nel turno che lo ha generato, non lo si
  // riporta dentro la conversazione.
  const thinking = body.thinking === true;

  const input: Record<string, unknown> = {
    messages,
    max_completion_tokens: maxTokens,
    temperature: 0.2,
    chat_template_kwargs: { enable_thinking: thinking, clear_thinking: true },
  };
  if (body.json === true) {
    // Come responseMimeType application/json lato Gemini: per le stesure di eventi, dove
    // il corpo e' un oggetto JSON che il client legge campo per campo.
    input.response_format = { type: 'json_object' };
  }

  let result: WorkersAiChatResult | undefined;
  try {
    result = await c.env.AI.run(MODEL, input) as WorkersAiChatResult;
  } catch (err) {
    if (isDailyQuotaError(err)) {
      return c.json({
        error: 'La quota giornaliera del servizio AI di AILA e\' finita (la conta Cloudflare, ' +
          'che si azzera alle 00:00 UTC): l\'assistente torna disponibile domani.',
        reason: 'QUOTA_EXHAUSTED',
      }, 429);
    }
    // Non e' fine quota: e' il servizio (modello sovraccarico, errore interno...). Nel
    // messaggio finisce l'inizio reale dell'errore: se (come gia' capitato per Gemini)
    // compaiono problemi inattesi, in app si possono capire, senza indovinare.
    console.error(`[Ai] errore da Workers AI su ${MODEL}:`, messageOf(err));
    return c.json({
      error: `Il servizio AI non e\' stato in grado di rispondere: ${messageOf(err).slice(0, 240)}`,
      reason: 'AI_ERROR',
    }, 502);
  }

  const completionText = result?.choices?.[0]?.message?.content;
  const text = typeof completionText === 'string'
    ? completionText.trim()
    : typeof result?.response === 'string'
      ? result.response.trim()
      : '';
  if (!text) {
    return c.json({ error: 'Il modello ha risposto vuoto' , reason: 'AI_ERROR' }, 502);
  }
  return c.json({ text, model: MODEL });
});

export default ai;
