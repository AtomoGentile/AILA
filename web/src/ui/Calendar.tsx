// Calendario: eventi della classe con i filtri per categoria dell'app, e inserimento.
import { useState } from 'preact/hooks';
import type { CalendarEventDto, CalendarResponse, CreateEventResponse, EventCategory } from '@worker/contracts';
import { api } from '../lib/api';
import { getSession } from '../lib/session';
import { CATEGORIES, CATEGORY_LABELS, Empty, ErrorBox, Loading, PageHeader, formatDate, useAsync } from './common';

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function Calendar() {
  const [filter, setFilter] = useState<EventCategory | null>(null);
  const [showPast, setShowPast] = useState(false);
  const [adding, setAdding] = useState(false);
  const list = useAsync(
    () => api<CalendarResponse>(`/api/calendar${filter ? `?category=${filter}` : ''}`),
    [filter]
  );

  const today = todayIso();
  const events = (list.data?.events ?? []).filter((e) => showPast || e.eventDate >= today);

  // Raggruppati per mese.
  const groups = new Map<string, CalendarEventDto[]>();
  for (const e of events) {
    const key = e.eventDate.slice(0, 7);
    groups.set(key, [...(groups.get(key) ?? []), e]);
  }

  return (
    <section>
      <PageHeader
        title="Calendario"
        action={
          <button class="btn btn-primary btn-small" onClick={() => setAdding((v) => !v)}>
            {adding ? 'Annulla' : '+ Evento'}
          </button>
        }
      />
      {adding && (
        <NewEvent
          onDone={() => {
            setAdding(false);
            list.reload();
          }}
        />
      )}
      <div class="chips" role="toolbar" aria-label="Filtra per categoria">
        <button class={`filter ${filter === null ? 'on' : ''}`} onClick={() => setFilter(null)}>
          Tutti
        </button>
        {CATEGORIES.map((c) => (
          <button key={c} class={`filter cat-${c.toLowerCase()} ${filter === c ? 'on' : ''}`} onClick={() => setFilter(c)}>
            {CATEGORY_LABELS[c]}
          </button>
        ))}
      </div>
      <label class="check small">
        <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.currentTarget.checked)} />
        Mostra anche gli eventi passati
      </label>
      {list.loading && !list.data && <Loading />}
      {list.error && <ErrorBox message={list.error} onRetry={list.reload} />}
      {list.data && events.length === 0 && <Empty>Nessun evento.</Empty>}
      {[...groups.entries()].map(([month, items]) => (
        <div key={month}>
          <h2 class="month">
            {new Date(`${month}-01T00:00:00`).toLocaleDateString('it-IT', { month: 'long', year: 'numeric' })}
          </h2>
          <ul class="list">
            {items.map((e) => (
              <EventRow key={e.id} event={e} onChanged={list.reload} />
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}

function EventRow({ event: e, onChanged }: { event: CalendarEventDto; onChanged: () => void }) {
  const me = getSession()?.user;
  // Stesse regole del server: autore, Rappresentante, o evento generato dall'AI.
  const canDelete = !!me && (e.isAiGenerated || e.createdBy === me.id || me.role === 'REPRESENTATIVE');

  async function remove() {
    if (!confirm(`Eliminare "${e.title}"?`)) return;
    try {
      await api(`/api/calendar/${e.id}`, { method: 'DELETE' });
      onChanged();
    } catch (err) {
      alert((err as Error).message);
    }
  }

  return (
    <li class={`card event cat-${e.category.toLowerCase()}`}>
      <div class="row between">
        <span class="small">
          <strong>{formatDate(e.eventDate, true)}</strong>
          {e.startTime ? ` · ${e.startTime}` : ''}
        </span>
        <span class={`chip cat-${e.category.toLowerCase()}`}>{CATEGORY_LABELS[e.category] ?? e.category}</span>
      </div>
      <h3>{e.title}</h3>
      {e.notes && <p class="muted small">{e.notes}</p>}
      <div class="row between">
        <span class="muted small">{e.isAiGenerated ? 'Da una circolare (AI)' : e.isForAll ? 'Per tutta la classe' : 'Per alcune persone'}</span>
        {canDelete && (
          <button class="btn btn-small btn-ghost danger" onClick={remove}>
            Elimina
          </button>
        )}
      </div>
    </li>
  );
}

function NewEvent({ onDone }: { onDone: () => void }) {
  const [title, setTitle] = useState('');
  const [date, setDate] = useState(todayIso());
  const [time, setTime] = useState('');
  const [category, setCategory] = useState<EventCategory>('VERIFICA');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(ev: Event) {
    ev.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api<CreateEventResponse>('/api/calendar', {
        method: 'POST',
        body: {
          title: title.trim(),
          eventDate: date,
          ...(time ? { startTime: time } : {}),
          category,
          ...(notes.trim() ? { notes: notes.trim() } : {}),
        },
      });
      // Possibile doppione: il server non crea l'evento e lo dice.
      if (res.warning) setError(`Non aggiunto: ${res.warning}`);
      else onDone();
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
        <input value={title} required onInput={(e) => setTitle(e.currentTarget.value)} />
      </label>
      <div class="row gap">
        <label class="grow">
          Data
          <input type="date" value={date} required onInput={(e) => setDate(e.currentTarget.value)} />
        </label>
        <label>
          Ora
          <input type="time" value={time} onInput={(e) => setTime(e.currentTarget.value)} />
        </label>
      </div>
      <label>
        Categoria
        <select value={category} onChange={(e) => setCategory(e.currentTarget.value as EventCategory)}>
          {CATEGORIES.map((c) => (
            <option value={c} key={c}>
              {CATEGORY_LABELS[c]}
            </option>
          ))}
        </select>
      </label>
      <label>
        Note
        <textarea value={notes} rows={2} onInput={(e) => setNotes(e.currentTarget.value)} />
      </label>
      {error && <p class="form-error">{error}</p>}
      <button class="btn btn-primary" disabled={busy}>
        {busy ? 'Salvo…' : 'Aggiungi'}
      </button>
    </form>
  );
}
