// Porting di HeuristicClassification.kt: ripiego a parole chiave quando l'AI non risponde.
import type { RelevanceBadge } from '@worker/contracts';
import type { Classification } from './types';

const MAX_REASON_CHARS = 140;

export function shortenReason(reason: string): string {
  const firstLine = (reason.split(/\r?\n/)[0] ?? '').trim();
  return firstLine.length <= MAX_REASON_CHARS ? firstLine : firstLine.slice(0, MAX_REASON_CHARS).trimEnd() + '…';
}

const STUDENT_ADDRESSED = [
  'agli studenti', 'a tutti gli studenti', 'agli alunni', 'a tutti gli alunni', 'alle famiglie',
  'ai genitori', 'studenti e ai loro genitori', 'studenti e alle famiglie',
];

const CLASS_ADDRESSED = [
  '4csa', '4 csa', '4^csa', '4^ csa', 'classi quarte', 'tutte le classi', 'tutti gli studenti', 'tutte le componenti',
];

const STAFF_ONLY_ADDRESSED = [
  'solo ai docenti', 'ai soli docenti', 'soli docenti', 'riservata ai docenti', 'esclusivamente ai docenti',
  'solo docenti', 'ai soli assistenti', 'riservata al personale', 'esclusivamente al personale',
  'solo al personale ata', 'ai soli docenti e al personale ata',
];

const OPTIONAL_ACTIVITY = [
  'facoltativ', 'corso pomeridiano', 'adesione volontaria', 'adesione facoltativa', 'chi fosse interessato',
  'per gli studenti interessati', 'olimpiadi', 'open day', 'borsa di studio', 'borse di studio',
];

export const NOT_CONFIGURED_MESSAGE = 'Nessuna API Key AI configurata: aprila dalle Impostazioni.';

export function classifyHeuristic(
  circularNumber: number,
  title: string,
  text: string,
  failureReason: string | null,
  notConfiguredMessage = NOT_CONFIGURED_MESSAGE
): Classification {
  const lower = `${title} ${text}`.toLowerCase();
  const has = (list: string[]) => list.some((k) => lower.includes(k));

  let badge: RelevanceBadge;
  let summary: string;
  if (failureReason != null) {
    // Un fallimento reale si mostra sempre per primo.
    badge = 'POTENTIAL';
    summary = `Analisi AI non riuscita (${shortenReason(failureReason)}) — risultato di riserva, apri il PDF per controllare.`;
  } else if (has(CLASS_ADDRESSED) || has(STUDENT_ADDRESSED)) {
    badge = 'RELEVANT';
    summary = 'Rivolta agli studenti o a tutte le classi: contiene comunicazioni che ti riguardano.';
  } else if (has(STAFF_ONLY_ADDRESSED)) {
    badge = 'NOT_RELEVANT';
    summary = 'Indirizzata soltanto ai docenti o al personale della scuola.';
  } else if (has(OPTIONAL_ACTIVITY)) {
    badge = 'POTENTIAL';
    summary = 'Attività o iniziativa ad adesione facoltativa.';
  } else {
    badge = 'POTENTIAL';
    summary = notConfiguredMessage;
  }

  return { circularNumber, badge, summary, deadlines: [], isFallback: true, modelLabel: 'Euristica a parole chiave' };
}
