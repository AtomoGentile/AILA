// Porting di ModelJsonExtractor.kt: estrae (e ripara) l'oggetto JSON dalla risposta del modello.
export function extractJsonObject(raw: string): string | null {
  let text = raw.trim();
  const thinkEnd = text.indexOf('</think>');
  if (thinkEnd >= 0) text = text.slice(thinkEnd + '</think>'.length).trim();

  if (text.startsWith('```json')) text = text.slice(7);
  else if (text.startsWith('```')) text = text.slice(3);
  if (text.endsWith('```')) text = text.slice(0, -3);
  text = text.trim();

  const start = text.indexOf('{');
  if (start < 0) return null;

  // Una " dentro una stringa chiude davvero solo se seguita da : , } ] o fine testo;
  // altrimenti è una virgoletta copiata dal documento e va scappata.
  let repaired = '';
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (escaped) {
      repaired += c;
      escaped = false;
    } else if (c === '\\' && inString) {
      repaired += c;
      escaped = true;
    } else if (c === '"' && inString) {
      let j = i + 1;
      while (j < text.length && /\s/.test(text[j])) j++;
      const next = text[j];
      if (next === undefined || next === ':' || next === ',' || next === '}' || next === ']') {
        inString = false;
        repaired += c;
      } else {
        repaired += '\\' + c;
      }
    } else if (c === '"') {
      inString = true;
      repaired += c;
    } else if (inString) {
      repaired += c;
    } else if (c === '{') {
      depth++;
      repaired += c;
    } else if (c === '}') {
      depth--;
      repaired += c;
      if (depth === 0) return repaired;
    } else {
      repaired += c;
    }
  }
  return null;
}
