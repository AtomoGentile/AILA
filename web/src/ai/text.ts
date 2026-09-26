// Porting di PdfTextBudget.kt e AssistantContext.normalize (codice condiviso Kotlin).

// Marcatore che introduce il testo di un allegato PDF.
export const ATTACHMENT_TEXT_MARKER = '\n\n--- Allegato: ';

// Controllo, formato, uso privato, surrogati, non assegnati: come le CharCategory del Kotlin.
const NON_TEXT = /[\p{Cc}\p{Cf}\p{Co}\p{Cs}\p{Cn}]/u;

/** Ripulisce il testo estratto da un PDF prima di darlo al modello. */
export function cleanPdfTextForAi(text: string): string {
  let kept = '';
  // Per unità UTF-16, come il for sui Char in Kotlin.
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '\n') kept += c;
    else if (c === '\t' || c === ' ') kept += ' ';
    else if (!NON_TEXT.test(c)) kept += c;
  }
  return kept
    .replace(/[ ]+/g, ' ')
    .replace(/ ?\n ?/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/** Taglia a maxChars dividendo il budget fra documento principale e allegati. */
export function truncatePdfTextForAi(text: string, maxChars: number): string {
  if (text.length <= maxChars) return text;

  const markerIndex = text.indexOf(ATTACHMENT_TEXT_MARKER);
  if (markerIndex < 0) return text.slice(0, maxChars);

  const mainText = text.slice(0, markerIndex);
  const parts = text
    .slice(markerIndex)
    .split(ATTACHMENT_TEXT_MARKER)
    .slice(1)
    .map((p) => ATTACHMENT_TEXT_MARKER + p);

  const mainBudget = Math.min(Math.floor(maxChars / 2), mainText.length);
  const perAttachment = parts.length > 0 ? Math.floor((maxChars - mainBudget) / parts.length) : 0;
  return mainText.slice(0, mainBudget) + parts.map((p) => p.slice(0, perAttachment)).join('');
}

const ACCENTS: Record<string, string> = {
  à: 'a', á: 'a', â: 'a', ä: 'a',
  è: 'e', é: 'e', ê: 'e', ë: 'e',
  ì: 'i', í: 'i', î: 'i', ï: 'i',
  ò: 'o', ó: 'o', ô: 'o', ö: 'o',
  ù: 'u', ú: 'u', û: 'u', ü: 'u',
};

/** Minuscolo, senza accenti, tutto ciò che non è lettera/cifra diventa spazio. */
export function normalize(text: string): string {
  let out = '';
  for (const ch of text.toLowerCase()) {
    const r = ACCENTS[ch] ?? ch;
    out += /[\p{L}\p{N}]/u.test(r) ? r : ' ';
  }
  return out;
}
