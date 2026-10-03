// =============================================================================
// CIRCOLARE+ — Spaggiari Scraper Service
// Fetch the school circular board (HTML table) and sync to D1 + R2
// =============================================================================

import { forgetPdfFailures, markServerSummaryPlanned } from './summarizer';
import type { CircularAttachment, Env } from '../types';
import { notifyClass } from './fcm';

interface ScrapedAttachment {
  label: string;
  url: string;
}

interface ScrapedCircular {
  number: number;
  title: string;
  date: string;
  pdfUrl: string;
  attachments: ScrapedAttachment[];
}

// Entità HTML minime che compaiono nei testi/etichette della tabella (es. "&#160;" fra il nome
// della classe e l'anno, "&amp;" in un titolo). Non serve un decoder completo: la pagina è
// generata da un CMS e usa solo queste.
function decodeHtmlEntities(text: string): string {
  // Due giri: un testo gia' passato da un decoder puo' contenere ancora entita' ("&amp;#160;"
  // diventava "&#160;" e li' restava, visto in app come "Festa di sport - &#160;2026"). "&amp;"
  // va per ultimo, una volta per giro, altrimenti decodifica anche cio' che dovrebbe restare.
  const decodeOnce = (t: string): string =>
    t
      .replace(/&#(x[0-9a-f]+|\d+);/gi, (match, body: string) => {
        const code = body[0].toLowerCase() === 'x' ? parseInt(body.slice(1), 16) : parseInt(body, 10);
        return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : match;
      })
      .replace(/&nbsp;/gi, ' ')
      .replace(/&quot;/gi, '"')
      .replace(/&apos;/gi, "'")
      .replace(/&lt;/gi, '<')
      .replace(/&gt;/gi, '>')
      .replace(/&amp;/gi, '&');
  return decodeOnce(decodeOnce(text)).replace(/\s+/g, ' ').trim();
}

// Estrae tutti i link `<a href="...">label</a>` da un frammento HTML (la cella "Allegati" può
// contenerne più di uno, uno per riga/<p>). `baseUrl` risolve gli href relativi — la colonna
// "Allegati" a volte punta a un'altra pagina dello stesso sito invece che a un PDF.
function extractLinks(cellHtml: string, baseUrl: string): ScrapedAttachment[] {
  const links: ScrapedAttachment[] = [];
  const aRegex = /<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let match: RegExpExecArray | null;
  while ((match = aRegex.exec(cellHtml)) !== null) {
    const label = decodeHtmlEntities(match[2].replace(/<[^>]+>/g, ''));
    let url: string;
    try {
      const parsed = new URL(match[1], baseUrl);
      // Solo link web: l'app li apre nel browser, e un "javascript:" o "intent:" arrivato dal sito
      // della scuola (o da chi lo ha compromesso) non deve finire in mano ai telefoni.
      if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') continue;
      url = parsed.toString();
    } catch {
      continue;
    }
    if (url) links.push({ label: label || 'Allegato', url });
  }
  return links;
}

// Un download che non risponde non deve tenere fermo il giro del cron (e con lui tutte le
// circolari dopo): oltre il timeout la richiesta fallisce e si passa oltre.
const FETCH_TIMEOUT_MS = 60_000;
function fetchWithTimeout(url: string, init: RequestInit = {}): Promise<Response> {
  return fetch(url, { ...init, signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
}

// ---------------------------------------------------------------------------
// Parse HTML table from Spaggiari
// Colonne della tabella reale (iisprimolevi.edu.it/Spaggiari): Numero | Data | Oggetto | PDF | Allegati
// ---------------------------------------------------------------------------
function parseSpaggiariHtml(html: string, baseUrl: string): ScrapedCircular[] {
  const results: ScrapedCircular[] = [];

  // Match table rows — simplified regex; for production use a proper HTML parser
  const rowRegex = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
  const tdRegex = /<td[^>]*>([\s\S]*?)<\/td>/gi;
  const numRegex = /^\s*(\d+)\s*$/;

  let rowMatch: RegExpExecArray | null;
  while ((rowMatch = rowRegex.exec(html)) !== null) {
    const row = rowMatch[1];
    // Celle grezze (HTML non spogliato): servono intere per la colonna PDF e Allegati, dove
    // conta l'href e non solo il testo visibile.
    const cellsHtml: string[] = [];

    let tdMatch: RegExpExecArray | null;
    const tdRe = new RegExp(tdRegex.source, 'gi');
    while ((tdMatch = tdRe.exec(row)) !== null) {
      cellsHtml.push(tdMatch[1]);
    }

    if (cellsHtml.length < 4) continue;

    const cellText = (html: string) => decodeHtmlEntities(html.replace(/<[^>]+>/g, ''));

    const numMatch = numRegex.exec(cellText(cellsHtml[0]));
    if (!numMatch) continue;

    const number = parseInt(numMatch[1], 10);
    const title = cellText(cellsHtml[2]) || `Circolare ${number}`;

    // Date might be dd/mm/yyyy → convert to yyyy-mm-dd
    const rawDate = cellText(cellsHtml[1]);
    const dateParts = rawDate.split('/');
    const date =
      dateParts.length === 3
        ? `${dateParts[2]}-${dateParts[1].padStart(2, '0')}-${dateParts[0].padStart(2, '0')}`
        : new Date().toISOString().split('T')[0];

    // Colonna "PDF": di norma un solo link, il documento principale della circolare.
    const pdfLinks = extractLinks(cellsHtml[3], baseUrl);
    const pdfUrl = pdfLinks[0]?.url ?? '';

    // Colonna "Allegati": zero, uno o più link — es. "Allegato a/b/c/d", oppure un singolo
    // link "Allegati" che rimanda a un'altra pagina del sito invece che a un PDF diretto.
    const attachments = cellsHtml[4] ? extractLinks(cellsHtml[4], baseUrl) : [];

    if (number > 0 && pdfUrl) {
      results.push({ number, title, date, pdfUrl, attachments });
    }
  }

  return results;
}

// ---------------------------------------------------------------------------
// Scarica & mette in cache su R2 gli allegati PDF di una circolare (colonna "Allegati" di
// Spaggiari). Un allegato che è direttamente un PDF viene scaricato e messo in cache come il
// documento principale; uno che punta altrove (es. un'altra pagina del sito) resta un link
// esterno — l'app lo apre nel browser di sistema invece di provare a mostrarlo dentro l'app.
// Usata sia per le circolari nuove sia per il backfill di quelle già note (vedi sotto).
// ---------------------------------------------------------------------------
async function resolveAttachments(
  env: Env,
  circularNumber: number,
  scrapedAttachments: ScrapedAttachment[]
): Promise<CircularAttachment[]> {
  const attachments: CircularAttachment[] = [];
  for (let i = 0; i < scrapedAttachments.length; i++) {
    const att = scrapedAttachments[i];
    const isPdf = /\.pdf(\?|$)/i.test(att.url);

    if (!isPdf) {
      attachments.push({ label: att.label, url: att.url });
      continue;
    }

    const attKey = `circulars/${circularNumber}-allegati/${i}.pdf`;
    try {
      const attRes = await fetchWithTimeout(att.url);
      if (attRes.ok && attRes.body) {
        await env.CIRCULARS_BUCKET.put(attKey, attRes.body, {
          httpMetadata: { contentType: 'application/pdf' },
          customMetadata: { circularNumber: String(circularNumber), label: att.label },
        });
        attachments.push({ label: att.label, pdfKey: attKey });
      } else {
        console.warn(`[Spaggiari] Allegato "${att.label}" di ${circularNumber} non scaricabile (HTTP ${attRes.status})`);
        attachments.push({ label: att.label, url: att.url });
      }
    } catch (err) {
      console.error(`[Spaggiari] Errore download allegato "${att.label}" di ${circularNumber}:`, err);
      attachments.push({ label: att.label, url: att.url });
    }
  }
  return attachments;
}

// ---------------------------------------------------------------------------
// Main sync function — called by cron trigger
// ---------------------------------------------------------------------------

/** Circolari recenti di cui si ricontrolla ogni giro che il PDF sia davvero su R2. */
const PDF_RECHECK_WINDOW = 30;

export async function syncSpaggiariCirculars(env: Env): Promise<void> {
  const baseUrl = env.SPAGGIARI_URL;
  if (!baseUrl) {
    console.log('[Spaggiari] SPAGGIARI_URL non configurata — skip');
    return;
  }

  let circulars: ScrapedCircular[];

  try {
    const res = await fetchWithTimeout(baseUrl, {
      headers: { 'User-Agent': 'AILA/3.0 (app della classe; circolari)' },
    });

    if (!res.ok) {
      console.error(`[Spaggiari] HTTP ${res.status} da ${baseUrl}`);
      return;
    }

    const html = await res.text();
    circulars = parseSpaggiariHtml(html, baseUrl);
    console.log(`[Spaggiari] Trovate ${circulars.length} circolari nella pagina`);
  } catch (err) {
    console.error('[Spaggiari] Errore fetch:', err);
    return;
  }

  const recent = new Set(
    circulars.map((c) => c.number).sort((a, b) => b - a).slice(0, PDF_RECHECK_WINDOW)
  );
  const added: ScrapedCircular[] = [];
  for (const circ of circulars) {
    // Una circolare che fallisce (D1, R2, rete) non deve bloccare quelle dopo: prima l'errore
    // interrompeva il giro e, riproponendosi identico ad ogni cron, le nuove restavano indietro.
    try {
      if (await syncOneCircular(env, circ, recent.has(circ.number))) added.push(circ);
    } catch (err) {
      console.error(`[Spaggiari] Circolare ${circ.number}: errore, si passa alla prossima`, err);
    }
  }

  // Una sola notifica per giro, anche con piu' circolari nuove: ogni notifica e' una richiesta
  // per dispositivo, e sul piano Free un'esecuzione ne puo' fare al massimo 50 in tutto.
  if (added.length > 0) {
    added.sort((a, b) => b.number - a.number);
    const newest = added[0];
    const title = added.length === 1 ? 'Nuova Circolare' : `${added.length} nuove circolari`;
    const body =
      added.length === 1
        ? `Circolare n. ${newest.number}: ${newest.title}`
        : added.map((c) => `n. ${c.number}: ${c.title}`).join(' · ').slice(0, 500);
    try {
      await notifyClass(env, title, body, { action: 'new_circular', circular_number: String(newest.number) });
    } catch (err) {
      console.error('[Spaggiari] Push delle circolari nuove non inviata:', err);
    }
  }
}

/** Scarica il PDF principale su R2. `true` se ora c'e'. */
async function cachePdf(env: Env, circ: { number: number; title: string; pdfUrl: string }, r2Key: string): Promise<boolean> {
  try {
    const pdfRes = await fetchWithTimeout(circ.pdfUrl);
    if (pdfRes.ok && pdfRes.body) {
      await env.CIRCULARS_BUCKET.put(r2Key, pdfRes.body, {
        httpMetadata: { contentType: 'application/pdf' },
        customMetadata: { circularNumber: String(circ.number), title: circ.title },
      });
      console.log(`[Spaggiari] PDF ${circ.number} salvato in R2: ${r2Key}`);
      return true;
    }
    console.warn(`[Spaggiari] PDF ${circ.number} non scaricabile (HTTP ${pdfRes.status})`);
  } catch (err) {
    console.error(`[Spaggiari] Errore download PDF ${circ.number}:`, err);
  }
  return false;
}

/** `true` se la circolare e' nuova ed e' stata aggiunta. */
async function syncOneCircular(env: Env, circ: ScrapedCircular, isRecent: boolean): Promise<boolean> {
  // 1. Deduplication check in D1
  const existing = await env.DB.prepare('SELECT number, attachments_json, r2_pdf_key FROM circulars WHERE number = ?')
    .bind(circ.number)
    .first<{ number: number; attachments_json: string; r2_pdf_key: string }>();

  if (existing) {
    // PDF mancante (download fallito o in timeout la prima volta): prima la circolare restava
    // "gia' nota" con il PDF assente per sempre. Per le recenti si ricontrolla a ogni giro.
    if (isRecent && existing.r2_pdf_key && !(await env.CIRCULARS_BUCKET.head(existing.r2_pdf_key))) {
      if (await cachePdf(env, circ, existing.r2_pdf_key)) {
        await forgetPdfFailures(env, circ.number);
        console.log(`[Spaggiari] PDF recuperato per la circolare già nota ${circ.number}`);
      }
    }

    // Backfill: la colonna attachments_json è arrivata dopo che queste circolari erano già
    // state salvate (lo scraper leggeva solo il PDF principale), quindi restano per sempre a
    // "[]" — questo dedup le salta come "già note" prima ancora di guardare se Spaggiari ha
    // allegati che non sono mai stati scaricati. Si aggiornano solo quelle che ne hanno
    // davvero bisogno (JSON vuoto ma la pagina ne mostra), non si rifà il lavoro ad ogni giro.
    const hasStoredAttachments = existing.attachments_json && existing.attachments_json !== '[]';
    if (!hasStoredAttachments && circ.attachments.length > 0) {
      const attachments = await resolveAttachments(env, circ.number, circ.attachments);
      await env.DB.prepare('UPDATE circulars SET attachments_json = ? WHERE number = ?')
        .bind(JSON.stringify(attachments), circ.number)
        .run();
      console.log(`[Spaggiari] Allegati recuperati per la circolare già nota ${circ.number}`);
    }
    return false;
  }

  // 2. Download PDF and cache in R2 (se fallisce si riprova ai giri successivi, vedi sopra)
  const r2Key = `circulars/${circ.number}.pdf`;
  await cachePdf(env, circ, r2Key);

  // 3. Download & cache attachments (colonna "Allegati": zero, uno o più per circolare)
  const attachments = await resolveAttachments(env, circ.number, circ.attachments);

  // 4. Save metadata in D1
  await env.DB.prepare(
    'INSERT INTO circulars (number, title, publish_date, r2_pdf_key, original_url, attachments_json) VALUES (?, ?, ?, ?, ?, ?)'
  )
    .bind(circ.number, circ.title, circ.date, r2Key, circ.pdfUrl, JSON.stringify(attachments))
    .run();

  // 5. Il riassunto lo fa summarizePendingCirculars subito dopo questo giro (index.ts), dopo la
  //    notifica. Intanto si segna che il server ci sta pensando: chi apre la circolare nel
  //    frattempo non ne fa partire un secondo in parallelo.
  try {
    await markServerSummaryPlanned(env, circ.number);
  } catch (err) {
    console.error(`[Spaggiari] Circolare ${circ.number}: prenotazione del riassunto non riuscita`, err);
  }

  console.log(`[Spaggiari] Circolare ${circ.number} aggiunta`);
  return true;
}
