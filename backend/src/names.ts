// =============================================================================
// Nomi delle persone — grafia uniforme: ogni pezzo con la prima lettera maiuscola
// =============================================================================

/**
 * Prima lettera maiuscola in ogni pezzo del nome, dalla seconda in poi sempre minuscola.
 *
 * I nomi composti hanno piu' pezzi: "de paoli" non deve restare "De paoli" con la sola
 * prima lettera a posto, anche il secondo pezzo ha la sua maiuscola ("De Paoli"). Vale
 * dentro la parola dopo apostrofo ("d'angelo" → "D'Angelo") e trattino
 * ("rossi-verdi" → "Rossi-Verdi").
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
  const words = value.replace(/\s+/g, ' ').trim().split(' ').filter(Boolean);
  return words.map((word) => wordOneCase(word)).join(' ');
}

/**
 * Un pezzo del nome: maiuscole all'inizio e dopo apostrofo o trattino, il resto minuscolo.
 */
function wordOneCase(word: string): string {
  return word.toLowerCase().replace(/(^|[’'-])(\p{L})/gu, (_match, separator: string, letter: string) =>
    separator + letter.toUpperCase(),
  );
}

/**
 * "Nome Cognome": le due parti passano da [nameCase], quelle vuote o di soli spazi
 * sparisce (cosi' un nome senza cognome non finisce col doppio spazio).
 */
export function fullName(first: string | null | undefined, last: string | null | undefined): string {
  return [nameCase(first ?? ''), nameCase(last ?? '')].filter(Boolean).join(' ');
}
