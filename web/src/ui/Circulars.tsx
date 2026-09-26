// Circolari: lista con il badge dell'analisi condivisa.
import { useEffect, useState } from 'preact/hooks';
import type { CircularAnalysisDto, CircularDto, CircularsResponse } from '@worker/contracts';
import { api } from '../lib/api';
import { Empty, ErrorBox, Loading, PageHeader, RelevanceChip, formatDate, useAsync } from './common';

const PAGE = 50;

export function Circulars() {
  const [items, setItems] = useState<CircularDto[]>([]);
  const [total, setTotal] = useState(0);
  const [more, setMore] = useState(false);
  const [query, setQuery] = useState('');

  const first = useAsync(() => api<CircularsResponse>(`/api/circulars?limit=${PAGE}&offset=0`));
  // Analisi già fatte da compagni o dal server (solo per i badge in lista).
  const analyses = useAsync(() =>
    api<{ analyses: CircularAnalysisDto[] }>('/api/circulars/analyses?since=1970-01-01%2000:00:00').then(
      (r) => new Map(r.analyses.map((a) => [a.circularNumber, a]))
    )
  );

  useEffect(() => {
    if (first.data) {
      setItems(first.data.circulars);
      setTotal(first.data.total);
    }
  }, [first.data]);

  async function loadMore() {
    setMore(true);
    try {
      const r = await api<CircularsResponse>(`/api/circulars?limit=${PAGE}&offset=${items.length}`);
      setItems((prev) => [...prev, ...r.circulars]);
    } finally {
      setMore(false);
    }
  }

  const q = query.trim().toLowerCase();
  const shown = q ? items.filter((c) => c.title.toLowerCase().includes(q) || String(c.number) === q) : items;

  return (
    <section>
      <PageHeader title="Circolari" />
      <input
        class="search"
        type="search"
        placeholder="Cerca per titolo o numero"
        value={query}
        onInput={(e) => setQuery(e.currentTarget.value)}
      />
      {first.loading && items.length === 0 && <Loading />}
      {first.error && <ErrorBox message={first.error} onRetry={first.reload} />}
      {!first.loading && !first.error && shown.length === 0 && <Empty>Nessuna circolare.</Empty>}
      <ul class="list">
        {shown.map((c) => {
          const a = analyses.data?.get(c.number);
          return (
            <li key={c.number}>
              <a class="card card-link" href={`#/circolari/${c.number}`}>
                <div class="row between">
                  <span class="muted small">
                    N. {c.number} · {formatDate(c.publishDate)}
                  </span>
                  {a && <RelevanceChip badge={a.badge} />}
                </div>
                <h3>{c.title}</h3>
                {c.attachments.length > 0 && <span class="muted small">📎 {c.attachments.length} allegati</span>}
              </a>
            </li>
          );
        })}
      </ul>
      {!q && items.length > 0 && items.length < total && (
        <button class="btn btn-block" onClick={loadMore} disabled={more}>
          {more ? 'Caricamento…' : 'Carica altre'}
        </button>
      )}
    </section>
  );
}
