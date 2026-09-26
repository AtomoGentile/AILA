// Pezzi di interfaccia condivisi fra le schermate.
import type { ComponentChildren } from 'preact';
import { useCallback, useEffect, useState } from 'preact/hooks';
import type { EventCategory, RelevanceBadge } from '@worker/contracts';

export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  reload: () => void;
}

/** Carica dati con stato di caricamento/errore; `deps` rilancia il caricamento. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => alive && setData(d))
      .catch((e: Error) => alive && setError(e.message))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

export function Loading({ label = 'Caricamento…' }: { label?: string }) {
  return (
    <div class="loading" role="status">
      <span class="spinner" aria-hidden="true" />
      {label}
    </div>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div class="error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button class="btn btn-small" onClick={onRetry}>
          Riprova
        </button>
      )}
    </div>
  );
}

export function Empty({ children }: { children: ComponentChildren }) {
  return <p class="empty">{children}</p>;
}

export function PageHeader({ title, action }: { title: string; action?: ComponentChildren }) {
  return (
    <header class="page-header">
      <h1>{title}</h1>
      {action}
    </header>
  );
}

// Date del server: "2026-09-26" (giorno locale), "2026-09-26 10:00:00" (UTC di D1) o ISO.
function parseServerDate(value: string): Date {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return new Date(`${value}T00:00:00`);
  if (/^\d{4}-\d{2}-\d{2} \d/.test(value)) return new Date(`${value.replace(' ', 'T')}Z`);
  return new Date(value);
}

export function formatDate(value: string, withWeekday = false): string {
  const d = parseServerDate(value);
  if (isNaN(d.getTime())) return value;
  return d.toLocaleDateString('it-IT', { day: 'numeric', month: 'short', year: 'numeric', ...(withWeekday ? { weekday: 'short' } : {}) });
}

export const BADGE_LABELS: Record<RelevanceBadge, string> = {
  RELEVANT: 'Ti riguarda',
  POTENTIAL: 'Potenziale interesse',
  NOT_RELEVANT: 'Non sembra riguardarti',
};

export function RelevanceChip({ badge }: { badge: RelevanceBadge }) {
  return <span class={`chip chip-${badge.toLowerCase()}`}>{BADGE_LABELS[badge]}</span>;
}

export const CATEGORY_LABELS: Record<EventCategory, string> = {
  VERIFICA: 'Verifica',
  INTERROGAZIONE: 'Interrogazione',
  PAGAMENTO: 'Pagamento',
  USCITA_DIDATTICA: 'Uscita didattica',
  AVVISO: 'Avviso',
  ALTRO: 'Altro',
};

export const CATEGORIES = Object.keys(CATEGORY_LABELS) as EventCategory[];

export function toCategory(value: string): EventCategory {
  const upper = value.toUpperCase() as EventCategory;
  return CATEGORIES.includes(upper) ? upper : 'ALTRO';
}
