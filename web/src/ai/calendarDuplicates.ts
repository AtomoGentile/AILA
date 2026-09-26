// Porting di CalendarDuplicates.kt: riconosce una scadenza già presente in calendario.
import { normalize } from './text';

const MIN_SIGNIFICANT_WORD_CHARS = 5;

export interface DeadlineLike {
  title: string;
  dueDate: string;
}

export interface EventLike {
  id: string;
  title: string;
  eventDate: string;
}

function significantWords(normalizedTitle: string): Set<string> {
  return new Set(normalizedTitle.split(' ').filter((w) => w.length >= MIN_SIGNIFICANT_WORD_CHARS));
}

/** Stesso giorno e titolo simile (uguale, contenuto, o una parola significativa in comune). */
export function findExisting<E extends EventLike>(deadline: DeadlineLike, events: E[]): E | null {
  const sameDay = events.filter((e) => e.eventDate.slice(0, 10) === deadline.dueDate);
  if (sameDay.length === 0) return null;

  const wanted = normalize(deadline.title).trim();
  const wantedWords = significantWords(wanted);
  return (
    sameDay.find((event) => {
      const other = normalize(event.title).trim();
      if (wanted === other) return true;
      if (wanted && other && (wanted.includes(other) || other.includes(wanted))) return true;
      for (const w of significantWords(other)) if (wantedWords.has(w)) return true;
      return false;
    }) ?? null
  );
}
