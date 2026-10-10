// Gita: documenti e link della classe con storico versioni. Lettura per tutti; scrittura solo per
// il Rappresentante (il server lo impone, qui si nascondono solo le azioni non permesse).
import { useState } from 'preact/hooks';
import type { GitaCategory, GitaFeedResponse, GitaItemDto, GitaReportDto, GitaVersionDto, GitaVersionsResponse } from '@worker/contracts';
import { apiBlob, api, apiForm, ApiError } from '../lib/api';
import { extractTextFromBytes } from '../lib/pdf';
import { formatUploadDay } from '../ai/gita';
import { Empty, ErrorBox, Icon, Loading, PageHeader, useAsync } from './common';
import { GitaChat } from './GitaChat';

const CATEGORIES: { id: GitaCategory; label: string }[] = [
  { id: 'PROGRAMMA', label: 'Programma' },
  { id: 'PREVENTIVO', label: 'Preventivo' },
  { id: 'SCADENZA', label: 'Scadenza' },
  { id: 'REGOLAMENTO', label: 'Regolamento' },
  { id: 'PAGAMENTO', label: 'Pagamento' },
  { id: 'ALTRO', label: 'Altro' },
];

type Panel =
  | { kind: 'versions'; item: GitaItemDto }
  | { kind: 'report'; item: GitaItemDto }
  | { kind: 'edit'; item: GitaItemDto }
  | { kind: 'replace'; item: GitaItemDto };

const MAX_BYTES = 100 * 1024 * 1024;

function errorText(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Operazione non riuscita. Riprova.';
}

export function Gita() {
  const feed = useAsync(() => api<GitaFeedResponse>('/api/gita'));
  const [panel, setPanel] = useState<Panel | null>(null);
  const [composing, setComposing] = useState<'document' | 'link' | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** Esegue una scrittura: occupato durante l'attesa, poi ricarica l'elenco e chiude il pannello. */
  async function write(message: string, action: () => Promise<unknown>) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice(message);
      setPanel(null);
      setComposing(null);
      feed.reload();
    } catch (e) {
      setNotice(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  if (feed.loading && !feed.data) return <Loading label="Carico la gita…" />;
  if (feed.error && !feed.data) return <ErrorBox message={feed.error} onRetry={feed.reload} />;
  const data = feed.data!;
  const canEdit = data.canEdit;
  const documents = data.items.filter((i) => i.kind === 'DOCUMENT');
  const links = data.items.filter((i) => i.kind === 'LINK');

  return (
    <section>
      <PageHeader
        title="Gita"
        action={
          canEdit ? (
            <div class="row-actions">
              <button class="btn btn-primary btn-small" disabled={busy} onClick={() => setComposing(composing === 'document' ? null : 'document')}>
                + PDF
              </button>
              <button class="btn btn-small" disabled={busy} onClick={() => setComposing(composing === 'link' ? null : 'link')}>
                + Link
              </button>
            </div>
          ) : undefined
        }
      />
      {notice && <p class="notice" role="status">{notice}</p>}
      {composing === 'document' && <DocumentForm busy={busy} onSubmit={(action) => write('Documento caricato.', action)} onCancel={() => setComposing(null)} />}
      {composing === 'link' && <LinkForm busy={busy} onSubmit={(action) => write('Link aggiunto.', action)} onCancel={() => setComposing(null)} />}

      <h2 class="column-title">Documenti</h2>
      {documents.length === 0 && <Empty>Nessun documento caricato.</Empty>}
      {documents.map((item) => (
        <ItemCard
          key={item.id}
          item={item}
          canEdit={canEdit}
          busy={busy}
          panel={panel}
          onPanel={setPanel}
          onWrite={write}
        />
      ))}

      <h2 class="column-title">Link utili</h2>
      {links.length === 0 && <Empty>Nessun link.</Empty>}
      {links.map((item) => (
        <ItemCard key={item.id} item={item} canEdit={canEdit} busy={busy} panel={panel} onPanel={setPanel} onWrite={write} />
      ))}

      {data.reports.length > 0 && (
        <>
          <h2 class="column-title">{canEdit ? 'Segnalazioni' : 'Le tue segnalazioni'}</h2>
          {data.reports.map((r) => (
            <ReportRow key={r.id} report={r} canEdit={canEdit} busy={busy} onWrite={write} />
          ))}
        </>
      )}

      <button class="assistant-fab" aria-label="Chiedi ad AILA Assistant della gita" onClick={() => setChatOpen(true)}>
        <Icon name="comment" />
        <span>AILA</span>
      </button>
      {chatOpen && <GitaChat onClose={() => setChatOpen(false)} />}
    </section>
  );
}

function ItemCard({
  item,
  canEdit,
  busy,
  panel,
  onPanel,
  onWrite,
}: {
  item: GitaItemDto;
  canEdit: boolean;
  busy: boolean;
  panel: Panel | null;
  onPanel: (p: Panel | null) => void;
  onWrite: (message: string, action: () => Promise<unknown>) => Promise<void>;
}) {
  const open = (kind: Panel['kind']) => onPanel(panel?.kind === kind && panel.item.id === item.id ? null : ({ kind, item } as Panel));
  const current = item.current;
  const isOpen = (kind: Panel['kind']) => panel?.kind === kind && panel.item.id === item.id;

  return (
    <article class="card gita-card">
      <div class="card-head">
        <h3>{item.title}</h3>
        {item.kind === 'DOCUMENT' && <span class="chip">{CATEGORIES.find((c) => c.id === item.category)?.label ?? 'Altro'}</span>}
      </div>
      <p class="muted">
        {item.kind === 'DOCUMENT'
          ? `Caricato il ${formatUploadDay(current?.uploadedAt ?? item.createdAt)}${current ? ` · versione ${current.versionNo}` : ''}`
          : `Aggiornato il ${formatUploadDay(current?.uploadedAt ?? item.createdAt)}`}
      </p>
      <div class="row-actions">
        {item.kind === 'DOCUMENT' ? (
          <button class="btn btn-small" disabled={!current?.hasFile || busy} onClick={() => current && openVersionFile(current.id)}>
            Apri
          </button>
        ) : (
          current?.url && (
            <a class="btn btn-small" href={current.url} target="_blank" rel="noopener noreferrer">
              Apri link
            </a>
          )
        )}
        <button class="btn btn-small" onClick={() => open('versions')}>Storico</button>
        <button class="btn btn-small" onClick={() => open('report')}>Segnala</button>
        {canEdit && (
          <>
            <button class="btn btn-small" onClick={() => open('replace')}>
              {item.kind === 'DOCUMENT' ? 'Sostituisci' : 'Nuovo indirizzo'}
            </button>
            <button class="btn btn-small" onClick={() => open('edit')}>Modifica</button>
            <button
              class="btn btn-small btn-danger"
              disabled={busy}
              onClick={() => {
                if (confirm(`Ritirare «${item.title}»? Lo storico resta consultabile dal Rappresentante.`)) {
                  void onWrite('Voce ritirata.', () => api(`/api/gita/items/${item.id}`, { method: 'DELETE' }));
                }
              }}
            >
              Ritira
            </button>
          </>
        )}
      </div>
      {isOpen('versions') && <VersionsPanel item={item} />}
      {isOpen('report') && <ReportForm item={item} busy={busy} onSubmit={(reason) => onWrite('Segnalazione inviata. Riceverai lo stato.', () => api(`/api/gita/items/${item.id}/reports`, { method: 'POST', body: { reason } }))} onCancel={() => onPanel(null)} />}
      {isOpen('edit') && <EditForm item={item} busy={busy} onSubmit={(title, category) => onWrite('Modifiche salvate.', () => api(`/api/gita/items/${item.id}`, { method: 'PATCH', body: { title, category } }))} onCancel={() => onPanel(null)} />}
      {isOpen('replace') && (
        item.kind === 'DOCUMENT' ? (
          <ReplaceFileForm busy={busy} onSubmit={(action) => onWrite('Nuova versione caricata.', action)} onCancel={() => onPanel(null)} itemId={item.id} />
        ) : (
          <LinkAddressForm busy={busy} initial={current?.url ?? ''} onSubmit={(url) => onWrite('Indirizzo aggiornato.', () => api(`/api/gita/items/${item.id}/versions`, { method: 'POST', body: { url } }))} onCancel={() => onPanel(null)} />
        )
      )}
    </article>
  );
}

/** Apre il PDF di una versione con il login (niente URL pubblico per la Gita). */
async function openVersionFile(versionId: string) {
  try {
    const blob = await apiBlob(`/api/gita/versions/${versionId}/file`);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    alert(errorText(e));
  }
}

function VersionsPanel({ item }: { item: GitaItemDto }) {
  const versions = useAsync(() => api<GitaVersionsResponse>(`/api/gita/items/${item.id}/versions`).then((r) => r.versions));
  if (versions.loading && !versions.data) return <Loading label="Storico…" />;
  if (versions.error) return <ErrorBox message={versions.error} onRetry={versions.reload} />;
  return (
    <ol class="versions">
      {(versions.data ?? []).map((v: GitaVersionDto) => (
        <li key={v.id}>
          <strong>Versione {v.versionNo}</strong>
          {v.id === item.current?.id && <span class="chip">attuale</span>}
          <span class="muted"> · caricata il {formatUploadDay(v.uploadedAt)}</span>
          {v.note && <p>{v.note}</p>}
          {v.hasFile && (
            <button class="btn btn-small" onClick={() => void openVersionFile(v.id)}>
              Apri questa versione
            </button>
          )}
          {v.url && (
            <a class="btn btn-small" href={v.url} target="_blank" rel="noopener noreferrer">
              Apri link
            </a>
          )}
        </li>
      ))}
    </ol>
  );
}

function ReportRow({ report, canEdit, busy, onWrite }: { report: GitaReportDto; canEdit: boolean; busy: boolean; onWrite: (m: string, a: () => Promise<unknown>) => Promise<void> }) {
  return (
    <div class="card">
      <strong>{report.itemTitle}</strong>
      <p>{report.reason}</p>
      <p class="muted">
        {report.status === 'RESOLVED'
          ? `Risolta il ${formatUploadDay(report.resolvedAt ?? report.createdAt)}`
          : `In attesa · inviata il ${formatUploadDay(report.createdAt)}`}
      </p>
      {canEdit && report.status === 'OPEN' && (
        <button class="btn btn-small" disabled={busy} onClick={() => void onWrite('Segnalazione risolta.', () => api(`/api/gita/reports/${report.id}`, { method: 'PATCH', body: { status: 'RESOLVED' } }))}>
          Segna come risolta
        </button>
      )}
    </div>
  );
}

type Action = () => Promise<unknown>;

function DocumentForm({ busy, onSubmit, onCancel }: { busy: boolean; onSubmit: (action: Action) => void; onCancel: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<GitaCategory>('PROGRAMMA');
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<string | null>(null);

  async function submit(e: Event) {
    e.preventDefault();
    if (!file) return setProblem('Scegli un file PDF.');
    if (file.type !== 'application/pdf') return setProblem('Solo file PDF.');
    if (file.size > MAX_BYTES) return setProblem('File troppo grande (massimo 100 MB).');
    const form = new FormData();
    form.append('title', title.trim() || file.name.replace(/\.pdf$/i, ''));
    form.append('category', category);
    form.append('file', file, file.name);
    form.append('text', await textOf(file));
    if (note.trim()) form.append('note', note.trim());
    onSubmit(() => apiForm('/api/gita/items', form));
  }

  return (
    <form class="card form" onSubmit={submit}>
      <label>File PDF<input type="file" accept="application/pdf" onChange={(e) => setFile(e.currentTarget.files?.[0] ?? null)} /></label>
      <label>Titolo<input value={title} onInput={(e) => setTitle(e.currentTarget.value)} maxLength={200} /></label>
      <label>Tipo
        <select value={category} onChange={(e) => setCategory(e.currentTarget.value as GitaCategory)}>
          {CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
        </select>
      </label>
      <label>Nota (facoltativa)<input value={note} onInput={(e) => setNote(e.currentTarget.value)} maxLength={1000} /></label>
      {problem && <p class="error">{problem}</p>}
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy}>Carica</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

function ReplaceFileForm({ busy, itemId, onSubmit, onCancel }: { busy: boolean; itemId: string; onSubmit: (action: Action) => void; onCancel: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  async function submit(e: Event) {
    e.preventDefault();
    if (!file) return setProblem('Scegli un file PDF.');
    if (file.type !== 'application/pdf') return setProblem('Solo file PDF.');
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('text', await textOf(file));
    onSubmit(() => apiForm(`/api/gita/items/${itemId}/versions`, form));
  }
  return (
    <form class="card form" onSubmit={submit}>
      <label>Nuovo file PDF<input type="file" accept="application/pdf" onChange={(e) => setFile(e.currentTarget.files?.[0] ?? null)} /></label>
      <p class="muted">La versione attuale resta nello storico.</p>
      {problem && <p class="error">{problem}</p>}
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy}>Carica nuova versione</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

function LinkForm({ busy, onSubmit, onCancel }: { busy: boolean; onSubmit: (action: Action) => void; onCancel: () => void }) {
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  return (
    <form class="card form" onSubmit={(e) => { e.preventDefault(); onSubmit(() => api('/api/gita/items', { method: 'POST', body: { title: title.trim(), url: url.trim() } })); }}>
      <label>Titolo<input value={title} onInput={(e) => setTitle(e.currentTarget.value)} maxLength={200} required /></label>
      <label>Indirizzo (https://…)<input type="url" value={url} onInput={(e) => setUrl(e.currentTarget.value)} required /></label>
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy}>Salva</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

function LinkAddressForm({ busy, initial, onSubmit, onCancel }: { busy: boolean; initial: string; onSubmit: (url: string) => void; onCancel: () => void }) {
  const [url, setUrl] = useState(initial);
  return (
    <form class="card form" onSubmit={(e) => { e.preventDefault(); onSubmit(url.trim()); }}>
      <label>Nuovo indirizzo<input type="url" value={url} onInput={(e) => setUrl(e.currentTarget.value)} required /></label>
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy}>Salva</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

function EditForm({ item, busy, onSubmit, onCancel }: { item: GitaItemDto; busy: boolean; onSubmit: (title: string, category: GitaCategory) => void; onCancel: () => void }) {
  const [title, setTitle] = useState(item.title);
  const [category, setCategory] = useState<GitaCategory>(item.category);
  return (
    <form class="card form" onSubmit={(e) => { e.preventDefault(); onSubmit(title.trim(), category); }}>
      <label>Titolo<input value={title} onInput={(e) => setTitle(e.currentTarget.value)} maxLength={200} required /></label>
      {item.kind === 'DOCUMENT' && (
        <label>Tipo
          <select value={category} onChange={(e) => setCategory(e.currentTarget.value as GitaCategory)}>
            {CATEGORIES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
          </select>
        </label>
      )}
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy}>Salva</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

function ReportForm({ item, busy, onSubmit, onCancel }: { item: GitaItemDto; busy: boolean; onSubmit: (reason: string) => void; onCancel: () => void }) {
  const [reason, setReason] = useState('');
  return (
    <form class="card form" onSubmit={(e) => { e.preventDefault(); onSubmit(reason.trim()); }}>
      <p class="muted">Cosa non va in «{item.title}»? Errore, data o importo sbagliati, documento vecchio.</p>
      <label>Motivo<textarea value={reason} onInput={(e) => setReason(e.currentTarget.value)} maxLength={500} required /></label>
      <div class="row-actions">
        <button class="btn btn-primary" disabled={busy || !reason.trim()}>Invia segnalazione</button>
        <button type="button" class="btn" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  );
}

/** Testo del PDF scelto, estratto nel browser. Vuoto se il PDF non ha testo (scansione). */
async function textOf(file: File): Promise<string> {
  try {
    return await extractTextFromBytes(new Uint8Array(await file.arrayBuffer()));
  } catch {
    return '';
  }
}
