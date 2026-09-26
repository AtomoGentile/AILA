// Stessi casi dei test Kotlin (CalendarDuplicatesTest, PdfTextCleaningTest...) sulla logica portata.
import { describe, expect, it } from 'vitest';
import { findExisting } from '../src/ai/calendarDuplicates';
import { classifyHeuristic } from '../src/ai/heuristic';
import { extractJsonObject } from '../src/ai/jsonExtractor';
import { isModelUnavailable, parseGeminiResponse } from '../src/ai/gemini';
import { ATTACHMENT_TEXT_MARKER, cleanPdfTextForAi, normalize, truncatePdfTextForAi } from '../src/ai/text';

const event = (id: string, title: string, eventDate: string) => ({ id, title, eventDate });
const deadline = (title: string, dueDate: string) => ({ title, dueDate });

describe('CalendarDuplicates', () => {
  it('stesso giorno e stesso titolo è doppione', () => {
    const events = [event('e1', 'Iscrizione sportelli didattici', '2026-09-23')];
    expect(findExisting(deadline('Iscrizione sportelli didattici', '2026-09-23'), events)?.id).toBe('e1');
  });

  it('titolo contenuto o parola significativa in comune', () => {
    const events = [event('e1', 'Festa di sport', '2026-09-13')];
    expect(findExisting(deadline('Festa di sport e attivita connesse', '2026-09-13'), events)?.id).toBe('e1');
    expect(findExisting(deadline('Adesioni Festa sportiva', '2026-09-13'), events)?.id).toBe('e1');
  });

  it("l'ora nella data dell'evento non impedisce il confronto", () => {
    const events = [event('e1', 'Festa di sport', '2026-09-13T00:00:00Z')];
    expect(findExisting(deadline('Festa di sport', '2026-09-13'), events)?.id).toBe('e1');
  });

  it('giorno diverso non è doppione', () => {
    expect(findExisting(deadline('Festa di sport', '2026-09-13'), [event('e1', 'Festa di sport', '2026-09-14')])).toBeNull();
  });

  it('stesso giorno ma cosa diversa non è doppione', () => {
    expect(findExisting(deadline('Pagamento gita', '2026-09-23'), [event('e1', 'Verifica di matematica', '2026-09-23')])).toBeNull();
  });

  it('calendario vuoto non ha doppioni', () => {
    expect(findExisting(deadline('Festa di sport', '2026-09-13'), [])).toBeNull();
  });

  it('gli accenti non contano', () => {
    expect(normalize('Attività è già')).toBe('attivita e gia');
  });
});

describe('PdfTextBudget', () => {
  it('toglie controllo, formato e uso privato', () => {
    expect(cleanPdfTextForAi('Festa\u0000 di­ sport ​2026')).toBe('Festa di sport 2026');
  });

  it('riduce spazi e righe vuote', () => {
    expect(cleanPdfTextForAi('  Riga   uno \n\n\n\n  Riga\tdue  ')).toBe('Riga uno\n\nRiga due');
  });

  it('tiene lettere accentate e punteggiatura', () => {
    const text = 'Il 13 settembre, è previsto un talk: “LA FORZA DELL’INATTESO”.';
    expect(cleanPdfTextForAi(text)).toBe(text);
  });

  it('il marcatore degli allegati sopravvive', () => {
    const text = 'Corpo del documento' + ATTACHMENT_TEXT_MARKER + 'modulo.pdf ---\n\nTesto allegato';
    expect(cleanPdfTextForAi(text)).toContain(ATTACHMENT_TEXT_MARKER);
  });

  it('divide il budget fra documento e allegati', () => {
    const text = 'A'.repeat(100) + ATTACHMENT_TEXT_MARKER + 'x ---' + 'B'.repeat(100) + ATTACHMENT_TEXT_MARKER + 'y ---' + 'C'.repeat(100);
    const out = truncatePdfTextForAi(text, 100);
    expect(out.length).toBeLessThanOrEqual(100);
    expect(out.startsWith('A'.repeat(50))).toBe(true);
    expect(out).toContain('x ---');
    expect(out).toContain('y ---');
  });

  it('testo corto resta intatto', () => {
    expect(truncatePdfTextForAi('breve', 100)).toBe('breve');
  });
});

describe('ModelJsonExtractor', () => {
  it('toglie ```json e <think>', () => {
    expect(extractJsonObject('<think>boh</think>\n```json\n{"a":1}\n```')).toBe('{"a":1}');
  });

  it('scappa le virgolette copiate dal testo', () => {
    const raw = '{"summary":"Istituto "Primo Levi" di Torino","badge":"RELEVANT"}';
    const json = extractJsonObject(raw)!;
    expect(JSON.parse(json)).toEqual({ summary: 'Istituto "Primo Levi" di Torino', badge: 'RELEVANT' });
  });

  it('niente oggetto, niente risultato', () => {
    expect(extractJsonObject('nessun json qui')).toBeNull();
  });
});

describe('HeuristicClassification', () => {
  it('gli studenti vincono sugli organi collegiali', () => {
    const r = classifyHeuristic(1, 'Festa di sport', 'A tutti gli studenti. Delibera del Consiglio di Istituto n. 16', null);
    expect(r.badge).toBe('RELEVANT');
    expect(r.isFallback).toBe(true);
  });

  it('solo docenti', () => {
    expect(classifyHeuristic(2, 'Collegio', 'Riservata ai docenti', null).badge).toBe('NOT_RELEVANT');
  });

  it('un errore reale viene mostrato per primo, in una riga', () => {
    const r = classifyHeuristic(3, 'x', 'a tutti gli studenti', 'HTTP 429\nstack trace lungo');
    expect(r.badge).toBe('POTENTIAL');
    expect(r.summary).toContain('HTTP 429');
    expect(r.summary).not.toContain('stack');
  });
});

describe('Risposta Gemini', () => {
  const wrap = (text: string) => JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] });

  it('legge badge, riassunto e scadenze', () => {
    const r = parseGeminiResponse(
      7,
      wrap(JSON.stringify({ badge: 'RELEVANT', summary: 'Uscita a Torino', deadlines: [{ title: 'Pagamento', dueDate: '2026-10-20', time: null, category: 'PAGAMENTO' }] })),
      'gemini-flash-latest'
    );
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.value.badge).toBe('RELEVANT');
      expect(r.value.deadlines).toEqual([{ title: 'Pagamento', dueDate: '2026-10-20', time: null, category: 'PAGAMENTO' }]);
      expect(r.value.modelLabel).toBe('Google Gemini (gemini-flash-latest)');
    }
  });

  it('un badge inventato è un fallimento, non un default', () => {
    const r = parseGeminiResponse(7, wrap('{"badge":"ALTA","summary":"x"}'), 'm');
    expect(r.ok).toBe(false);
  });

  it('503 e modello ritirato fanno provare il modello dopo; 400 no', () => {
    expect(isModelUnavailable(503, '')).toBe(true);
    expect(isModelUnavailable(404, '')).toBe(true);
    expect(isModelUnavailable(400, 'API key not valid')).toBe(false);
  });
});
