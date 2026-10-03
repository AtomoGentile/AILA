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

// Icone di linea al posto delle emoji: stesso tratto ovunque e colore dal testo vicino
// (`currentColor`), quindi seguono tema chiaro/scuro e stato attivo. Decorative per gli screen
// reader: il nome accessibile lo da' il testo o l'aria-label del controllo.
const ICON_PATHS = {
  circulars: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h4',
  board: 'M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.6.5 1 1.2 1.1 2.2h5c.1-1 .5-1.7 1.1-2.2A6 6 0 0 0 12 3z',
  calendar: 'M7 3v4M17 3v4M4 9h16M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
  seats: 'M4 5h7v6H4zM13 5h7v6h-7zM4 15h7v4H4zM13 15h7v4h-7z',
  settings: 'M4 7h10M18 7h2M4 17h4M12 17h8M16 5v4M10 15v4',
  up: 'M7 11v9H4v-9zM7 11l4-8a2 2 0 0 1 2 2v4h5.5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.3 20H7',
  down: 'M17 13V4h3v9zM17 13l-4 8a2 2 0 0 1-2-2v-4H5.5a2 2 0 0 1-2-2.3l1.2-7A2 2 0 0 1 6.7 4H17',
  comment: 'M20 12a8 8 0 0 1-11.6 7.1L4 20l1-4.2A8 8 0 1 1 20 12z',
  attachment: 'M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7L9.7 17.2a1.7 1.7 0 0 1-2.4-2.4l7.6-7.6',
  external: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  check: 'M5 12.5l4.5 4.5L19 7.5',
} as const;

export type IconName = keyof typeof ICON_PATHS;

export function Icon({ name, class: cls = 'icon' }: { name: IconName; class?: string }) {
  return (
    <svg class={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      <path d={ICON_PATHS[name]} />
    </svg>
  );
}
