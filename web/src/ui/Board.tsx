// Bacheca proposte: colonne per stato, voti, commenti, proposta anonima.
// I ruoli li applica il server; qui si nascondono solo le azioni non permesse.
import { useState } from 'preact/hooks';
import type { CommentsResponse, ProposalDto, ProposalOutcome, ProposalStatus, ProposalsResponse, VoteResponse } from '@worker/contracts';
import { api } from '../lib/api';
import { getSession } from '../lib/session';
import { Empty, ErrorBox, Loading, PageHeader, formatDate, useAsync } from './common';

const COLUMNS: { status: ProposalStatus; label: string }[] = [
  { status: 'NUOVA', label: 'Nuove' },
  { status: 'IN_ANALISI', label: 'In analisi' },
  { status: 'CHIUSA', label: 'Chiuse' },
];

export function Board() {
  const list = useAsync(() => api<ProposalsResponse>('/api/proposals'));
  const [composing, setComposing] = useState(false);

  return (
    <section>
      <PageHeader
        title="Bacheca"
        action={
          <button class="btn btn-primary btn-small" onClick={() => setComposing((v) => !v)}>
            {composing ? 'Annulla' : '+ Proponi'}
          </button>
        }
      />
      {composing && (
        <NewProposal
          onDone={() => {
            setComposing(false);
            list.reload();
          }}
        />
      )}
      {list.loading && !list.data && <Loading />}
      {list.error && <ErrorBox message={list.error} onRetry={list.reload} />}
      {list.data && (
        <div class="columns">
          {COLUMNS.map((col) => {
            const items = list.data!.proposals.filter((p) => p.status === col.status);
            return (
              <div class="column" key={col.status}>
                <h2 class="column-title">
                  {col.label} <span class="count">{items.length}</span>
                </h2>
                {items.length === 0 && <Empty>Nessuna proposta.</Empty>}
                {items.map((p) => (
                  <ProposalCard key={p.id} proposal={p} onChanged={list.reload} />
                ))}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function NewProposal({ onDone }: { onDone: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: Event) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api('/api/proposals', { method: 'POST', body: { title: title.trim(), description: description.trim(), isAnonymous: anonymous } });
      onDone();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form class="card form" onSubmit={submit}>
      <label>
        Titolo
        <input value={title} maxLength={200} required onInput={(e) => setTitle(e.currentTarget.value)} />
      </label>
      <label>
        Descrizione
        <textarea value={description} rows={4} required onInput={(e) => setDescription(e.currentTarget.value)} />
      </label>
      <label class="check">
        <input type="checkbox" checked={anonymous} onChange={(e) => setAnonymous(e.currentTarget.checked)} />
        Pubblica in forma anonima
      </label>
      <p class="muted small">
        L'anonimato vale per tutti: l'autore può essere svelato solo con l'approvazione di due Rappresentanti e della Guardia di
        Sicurezza.
      </p>
      {error && <p class="form-error">{error}</p>}
      <button class="btn btn-primary" disabled={busy}>
        {busy ? 'Pubblico…' : 'Pubblica'}
      </button>
    </form>
  );
}

function ProposalCard({ proposal: p, onChanged }: { proposal: ProposalDto; onChanged: () => void }) {
  const me = getSession()?.user;
  const isRep = me?.role === 'REPRESENTATIVE';
  const isAuthor = !!me && p.authorId === me.id;
  const [votes, setVotes] = useState({ up: p.upVotes, down: p.downVotes, mine: p.myVote });
  const [showComments, setShowComments] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const closed = p.status === 'CHIUSA';

  async function vote(v: 1 | -1) {
    const next = votes.mine === v ? 0 : v;
    try {
      const r = await api<VoteResponse>(`/api/proposals/${p.id}/vote`, { method: 'POST', body: { voteType: next } });
      setVotes({ up: r.upVotes, down: r.downVotes, mine: next === 0 ? null : next });
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function setStatus(status: ProposalStatus, outcome?: ProposalOutcome) {
    try {
      await api(`/api/proposals/${p.id}/status`, { method: 'PUT', body: { status, outcome: outcome ?? null } });
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function remove() {
    if (!confirm('Eliminare questa proposta? Voti e commenti andranno persi.')) return;
    try {
      await api(`/api/proposals/${p.id}`, { method: 'DELETE' });
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <article class="card proposal">
      <h3>{p.title}</h3>
      <p class="muted small">
        {p.isAnonymous && isAuthor ? 'Tu (anonima per gli altri)' : (p.authorName ?? 'Anonimo')}
        {p.identityRevealed && ' (svelato)'} · {formatDate(p.createdAt)}
        {p.modifiedByRep && ' · modificata'}
      </p>
      {closed && p.outcome && <span class={`chip chip-${p.outcome === 'ACCETTATA' ? 'relevant' : 'not_relevant'}`}>{p.outcome === 'ACCETTATA' ? 'Accettata' : 'Rifiutata'}</span>}
      <p class="proposal-text">{p.description}</p>
      <div class="row gap wrap">
        <button class={`btn btn-small ${votes.mine === 1 ? 'btn-on' : ''}`} disabled={closed} onClick={() => vote(1)} aria-pressed={votes.mine === 1}>
          👍 {votes.up}
        </button>
        <button class={`btn btn-small ${votes.mine === -1 ? 'btn-on' : ''}`} disabled={closed} onClick={() => vote(-1)} aria-pressed={votes.mine === -1}>
          👎 {votes.down}
        </button>
        <button class="btn btn-small btn-ghost" onClick={() => setShowComments((v) => !v)}>
          💬 {p.commentCount}
        </button>
        {(isAuthor || isRep) && (
          <button class="btn btn-small btn-ghost danger" onClick={remove}>
            Elimina
          </button>
        )}
      </div>
      {isRep && (
        <div class="row gap wrap rep-actions">
          {p.status === 'NUOVA' && (
            <button class="btn btn-small" onClick={() => setStatus('IN_ANALISI')}>
              In analisi
            </button>
          )}
          {!closed && (
            <>
              <button class="btn btn-small" onClick={() => setStatus('CHIUSA', 'ACCETTATA')}>
                Accetta
              </button>
              <button class="btn btn-small" onClick={() => setStatus('CHIUSA', 'RIFIUTATA')}>
                Rifiuta
              </button>
            </>
          )}
          {closed && (
            <button class="btn btn-small" onClick={() => setStatus('NUOVA')}>
              Riapri
            </button>
          )}
        </div>
      )}
      {error && <p class="form-error">{error}</p>}
      {showComments && <Comments proposalId={p.id} />}
    </article>
  );
}

function Comments({ proposalId }: { proposalId: string }) {
  const list = useAsync(() => api<CommentsResponse>(`/api/proposals/${proposalId}/comments`), [proposalId]);
  const [text, setText] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function send(e: Event) {
    e.preventDefault();
    setError(null);
    try {
      await api(`/api/proposals/${proposalId}/comments`, { method: 'POST', body: { content: text.trim(), isAnonymous: anonymous } });
      setText('');
      list.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div class="comments">
      {list.loading && !list.data && <Loading />}
      {list.error && <ErrorBox message={list.error} onRetry={list.reload} />}
      {list.data?.comments.map((c) => (
        <div class="comment" key={c.id}>
          <p class="small">
            <strong>{c.authorName}</strong> <span class="muted">· {formatDate(c.createdAt)}</span>
          </p>
          <p>{c.content}</p>
        </div>
      ))}
      <form onSubmit={send} class="comment-form">
        <textarea value={text} rows={2} maxLength={1000} required placeholder="Scrivi un commento" onInput={(e) => setText(e.currentTarget.value)} />
        <div class="row between">
          <label class="check small">
            <input type="checkbox" checked={anonymous} onChange={(e) => setAnonymous(e.currentTarget.checked)} />
            Anonimo
          </label>
          <button class="btn btn-small btn-primary">Invia</button>
        </div>
        {error && <p class="form-error">{error}</p>}
      </form>
    </div>
  );
}
