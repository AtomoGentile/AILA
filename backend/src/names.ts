// =============================================================================
// Nomi delle persone — grafia uniforme: prima lettera maiuscola, il resto minuscolo
// =============================================================================

/**
 * Prima lettera maiuscola, dalla seconda in poi sempre minuscola.
 *
 * Prima no: ognuno scriveva il nome a modo suo ("GIULIO", "marta", "sara", "Sara") e gli
 * elenchi ordinati alfabeticamente (mappa posti, "chi non ha ancora votato" della
 * Preferenze Sociali) mescolavano maiuscole e minuscole, perché in SQLite le lettere
 * maiuscole vengono prima di tutte le minuscole. Ora la grafia è una sola:
 *  - alla fonte: registrazione e richiesta di cambio nome salvano i nomi già con questa
 *    forma (vedi routes/auth.ts e routes/users.ts);
 *  - in lettura: chi compone i nomi per le risposte API passa da qui, così anche i nomi
 *    già scritti nel database con la vecchia grafia arrivano all'app sempre uguali.
 */
export function nameCase(value: string): string {
  const trimmed = value.replace(/\s+/g, ' ').trim();
  if (!trimmed) return '';
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1).toLowerCase();
}

/**
 * "Nome Cognome": le due parti passano da [nameCase], quelle vuote o di soli spazi
 * sparisce (cosi' un nome senza cognome non finisce col doppio spazio).
 */
export function fullName(first: string | null | undefined, last: string | null | undefined): string {
  return [nameCase(first ?? ''), nameCase(last ?? '')].filter(Boolean).join(' ');
}
