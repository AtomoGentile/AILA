// Dettaglio circolare: PDF dal Worker/R2, analisi AI (condivisa o fatta qui con la chiave
// personale), scadenze da aggiungere al calendario senza doppioni.
import { useEffect, useRef, useState } from 'preact/hooks';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import type {
  CalendarResponse,
  CircularAnalysisDto,
  CircularDto,
  CreateEventResponse,
  DeadlineDto,
  SaveAnalysisResponse,
} from '@worker/contracts';
import { classifyCircular } from '../ai/gemini';
import { findExisting } from '../ai/calendarDuplicates';
import { ATTACHMENT_TEXT_MARKER } from '../ai/text';
import type { Classification } from '../ai/types';
import { api, pdfUrl } from '../lib/api';
import { kv } from '../lib/db';
import { extractText, openPdf, renderPage } from '../lib/pdf';
import { studentContext } from '../lib/session';
import { ErrorBox, Loading, RelevanceChip, formatDate, toCategory, useAsync } from './common';

const MAX_RENDERED_PAGES = 30;

function fromDto(a: CircularAnalysisDto): Classification {
  return { circularNumber: a.circularNumber, badge: a.badge, summary: a.summary, deadlines: a.deadlines, isFallback: a.isFallback, modelLabel: a.modelLabel };
}

export function CircularDetail({ number }: { number: number }) {
  const circular = useAsync(() => api<CircularDto>(`/api/circulars/${number}`), [number]);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);
  const [analysis, setAnalysis] = useState<Classification | null>(null);
  const [analysisState, setAnalysisState] = useState<'loading' | 'idle' | 'running' | 'nokey'>('loading');

  // PDF principale
  useEffect(() => {
    if (!circular.data) return;
    let alive = true;
    setPdfError(null);
    openPdf(pdfUrl(circular.data.pdfKey))
      .then((d) => alive && setDoc(d))
      .catch((e: Error) => alive && setPdfError(e.message));
    return () => {
      alive = false;
    };
  }, [circular.data]);

  // Analisi: prima quella condivisa sul server, poi quella salvata qui, poi l'AI con la chiave.
  useEffect(() => {
    if (!circular.data || !doc) return;
    let alive = true;
    (async () => {
      setAnalysisState('loading');
      try {
        const server = await api<CircularAnalysisDto>(`/api/circulars/${number}/analysis`);
        if (alive) {
          setAnalysis(fromDto(server));
          setAnalysisState('idle');
        }
        return;
      } catch {
        // 404 (nessuna analisi) o offline: si prova con quella salvata qui.
      }
      const local = await kv.get<Classification>(`analysis:${number}`);
      if (local && !local.isFallback) {
        if (alive) {
          setAnalysis(local);
          setAnalysisState('idle');
        }
        return;
      }
      const key = (await kv.get<string>('geminiKey')) ?? '';
      if (!key) {
        if (alive) setAnalysisState('nokey');
        return;
      }
      if (alive) await runAnalysis(key, circular.data!, doc, () => alive);
    })();
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [circular.data, doc]);

  async function runAnalysis(key: string, c: CircularDto, d: PDFDocumentProxy, alive: () => boolean) {
    setAnalysisState('running');
    let text = await extractText(d);
    // Allegati PDF nello stesso testo, come nell'app; uno illeggibile si salta.
    for (const att of c.attachments) {
      if (!att.pdfKey) continue;
      try {
        const attText = await extractText(await openPdf(pdfUrl(att.pdfKey)));
        if (attText.trim()) text += `${ATTACHMENT_TEXT_MARKER}${att.label} ---\n\n${attText}`;
      } catch {
        // ignorato di proposito
      }
    }
    let result = await classifyCircular(key, c.number, c.title, text, studentContext());
    // Condivisione con la classe: il server tiene la migliore e, se ne ha una, la restituisce.
    if (!result.isFallback) {
      try {
        const saved = await api<SaveAnalysisResponse>(`/api/circulars/${c.number}/analysis`, {
          method: 'PUT',
          body: { badge: result.badge, summary: result.summary, deadlines: result.deadlines, isFallback: false, modelLabel: result.modelLabel },
        });
        if (!saved.stored && saved.current) result = fromDto(saved.current);
      } catch {
        // il risultato resta valido anche se il salvataggio fallisce
      }
    }
    await kv.set(`analysis:${c.number}`, result);
    if (alive()) {
      setAnalysis(result);
      setAnalysisState('idle');
    }
  }

  async function reanalyze() {
    const key = (await kv.get<string>('geminiKey')) ?? '';
    if (key && circular.data && doc) await runAnalysis(key, circular.data, doc, () => true);
  }

  if (circular.loading) return <Loading />;
  if (circular.error || !circular.data) return <ErrorBox message={circular.error ?? 'Circolare non trovata'} onRetry={circular.reload} />;
  const c = circular.data;

  return (
    <section>
      <a href="#/circolari" class="back">
        ‹ Circolari
      </a>
      <p class="muted small">
        N. {c.number} · {formatDate(c.publishDate)}
      </p>
      <h1 class="detail-title">{c.title}</h1>

      <div class="card ai-box">
        <div class="row between">
          <strong>Analisi AI</strong>
          {analysis && <RelevanceChip badge={analysis.badge} />}
        </div>
        {analysisState === 'loading' && <Loading label="Cerco un'analisi…" />}
        {analysisState === 'running' && <Loading label="Analizzo la circolare con Gemini…" />}
        {analysisState === 'nokey' && (
          <p class="muted">
            Nessuna analisi disponibile. Inserisci la tua chiave Google AI Studio nelle <a href="#/impostazioni">Impostazioni</a> per
            analizzarla.
          </p>
        )}
        {analysis && analysisState === 'idle' && (
          <>
            <p class="summary">{analysis.summary}</p>
            <p class="muted small">{analysis.modelLabel}</p>
            {analysis.deadlines.length > 0 && <Deadlines deadlines={analysis.deadlines} circularNumber={c.number} />}
            {analysis.isFallback && (
              <button class="btn btn-small" onClick={reanalyze}>
                Riprova l'analisi
              </button>
            )}
          </>
        )}
      </div>

      <div class="row gap wrap">
        <a class="btn" href={pdfUrl(c.pdfKey)} target="_blank" rel="noopener">
          Apri PDF
        </a>
        {c.attachments.map((att) =>
          att.pdfKey ? (
            <a class="btn btn-ghost" href={pdfUrl(att.pdfKey)} target="_blank" rel="noopener" key={att.label}>
              📎 {att.label}
            </a>
          ) : att.url ? (
            <a class="btn btn-ghost" href={att.url} target="_blank" rel="noopener noreferrer" key={att.label}>
              ↗ {att.label}
            </a>
          ) : null
        )}
      </div>

      {pdfError && <ErrorBox message={pdfError} />}
      {!doc && !pdfError && <Loading label="Carico il PDF…" />}
      {doc && <PdfPages doc={doc} />}
    </section>
  );
}

function PdfPages({ doc }: { doc: PDFDocumentProxy }) {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = container.current;
    if (!el) return;
    let cancelled = false;
    el.replaceChildren();
    (async () => {
      const width = Math.min(el.clientWidth || 360, 900);
      for (let i = 1; i <= Math.min(doc.numPages, MAX_RENDERED_PAGES) && !cancelled; i++) {
        const canvas = document.createElement('canvas');
        canvas.className = 'pdf-page';
        el.appendChild(canvas);
        await renderPage(doc, i, canvas, width);
      }
    })().catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [doc]);
  return (
    <>
      <div ref={container} class="pdf-pages" />
      {doc.numPages > MAX_RENDERED_PAGES && <p class="muted small">Mostrate le prime {MAX_RENDERED_PAGES} pagine: apri il PDF per il resto.</p>}
    </>
  );
}

function Deadlines({ deadlines, circularNumber }: { deadlines: DeadlineDto[]; circularNumber: number }) {
  const [status, setStatus] = useState<Record<number, string>>({});

  async function add(d: DeadlineDto, i: number) {
    setStatus((s) => ({ ...s, [i]: 'Aggiungo…' }));
    try {
      // Controllo doppioni anche lato client (il server guarda solo verifiche/interrogazioni).
      const { events } = await api<CalendarResponse>(`/api/calendar?from=${d.dueDate}&to=${d.dueDate}`);
      if (findExisting(d, events)) {
        setStatus((s) => ({ ...s, [i]: 'Già in calendario' }));
        return;
      }
      const res = await api<CreateEventResponse>('/api/calendar', {
        method: 'POST',
        body: {
          title: d.title,
          eventDate: d.dueDate,
          ...(d.time && /^\d{2}:\d{2}$/.test(d.time) ? { startTime: d.time } : {}),
          category: toCategory(d.category),
          isAiGenerated: true,
          notes: `Dalla circolare n. ${circularNumber}`,
        },
      });
      setStatus((s) => ({ ...s, [i]: res.warning ? `Non aggiunto: ${res.warning}` : 'Aggiunto ✓' }));
    } catch (e) {
      setStatus((s) => ({ ...s, [i]: (e as Error).message }));
    }
  }

  return (
    <ul class="deadlines">
      {deadlines.map((d, i) => (
        <li key={i}>
          <div>
            <strong>{d.title}</strong>
            <span class="muted small">
              {' '}
              · {formatDate(d.dueDate, true)}
              {d.time ? ` ore ${d.time}` : ''}
            </span>
          </div>
          {status[i] ? (
            <span class="small">{status[i]}</span>
          ) : (
            <button class="btn btn-small" onClick={() => add(d, i)}>
              Aggiungi al calendario
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}
