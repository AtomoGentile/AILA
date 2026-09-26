import { describe, expect, it } from 'vitest';
import { classIdForLabel, displayClassLabel, resolveForClass } from '../src/services/classAnalysis';
import { parseAnalysis } from '../src/services/summarizer';

const classes = [
  { id: 'DEFAULT_CLASS', label: '4 CSA' },
  { id: 'c5bia', label: '5 BIA' },
];

describe('etichette delle classi', () => {
  it('scrive la classe attaccata', () => {
    expect(displayClassLabel('4 CSA')).toBe('4^CSA');
    expect(displayClassLabel('4^CSA')).toBe('4^CSA');
  });

  it('riconosce le varianti scritte dal modello', () => {
    for (const label of ['4^CSA', '4^ CSA', '4 csa', '4ª CSA', '4CSA']) {
      expect(classIdForLabel(label, classes)).toBe('DEFAULT_CLASS');
    }
    expect(classIdForLabel('3^A', classes)).toBeNull();
    expect(classIdForLabel(42, classes)).toBeNull();
  });
});

describe('analisi per classe', () => {
  const response = JSON.stringify({
    badge: 'RELEVANT',
    summary: 'Convocazione dei Consigli di Classe dal 12 al 16 ottobre.',
    classes: [
      { class: '4^ CSA', badge: 'RELEVANT', note: 'Il Consiglio della 4^ CSA è il 14 ottobre alle 16:00.' },
      { class: '5^BIA', badge: 'RELEVANT', note: '' },
      { class: '1^Z', badge: 'NOT_RELEVANT', note: 'Classe non registrata.' },
    ],
    deadlines: [
      { title: 'Consiglio 4^ CSA', dueDate: '2026-10-14', time: '16:30', category: 'AVVISO', classes: ['4^CSA'] },
      { title: 'Consiglio 5^BIA', dueDate: '2026-10-15', time: null, category: 'AVVISO', classes: ['5^BIA'] },
      { title: 'Consiglio 1^Z', dueDate: '2026-10-16', time: null, category: 'AVVISO', classes: ['1^Z'] },
      { title: 'Assemblea di istituto', dueDate: '2026-10-20', time: null, category: 'AVVISO', classes: [] },
    ],
  });

  it('una sola risposta contiene i dati di ogni classe registrata', () => {
    const analysis = parseAnalysis(response, 'gemini-flash-latest', classes)!;
    expect(Object.keys(analysis.perClass).sort()).toEqual(['DEFAULT_CLASS', 'c5bia']);
    expect(analysis.perClass.DEFAULT_CLASS.note).toContain('4^CSA');
    // La scadenza di una classe non registrata si scarta.
    expect(analysis.deadlines.map((d) => d.title)).toEqual([
      'Consiglio 4^CSA',
      'Consiglio 5^BIA',
      'Assemblea di istituto',
    ]);
  });

  it('ogni classe vede la sua nota e solo le sue scadenze', () => {
    const analysis = parseAnalysis(response, 'gemini-flash-latest', classes)!;
    const stored = { ...analysis, perClass: analysis.perClass };

    const mine = resolveForClass(stored, 'DEFAULT_CLASS');
    expect(mine.summary).toContain('14 ottobre');
    expect(mine.deadlines.map((d) => d.title)).toEqual(['Consiglio 4^CSA', 'Assemblea di istituto']);
    expect(mine.deadlines[0]).not.toHaveProperty('classes');

    const other = resolveForClass(stored, 'c5bia');
    expect(other.summary).not.toContain('14 ottobre');
    expect(other.deadlines.map((d) => d.title)).toEqual(['Consiglio 5^BIA', 'Assemblea di istituto']);
  });

  it('senza dati per classe l\'analisi vale uguale per tutti', () => {
    const plain = {
      badge: 'POTENTIAL',
      summary: 'Corso pomeridiano.',
      deadlines: [{ title: 'Iscrizione', dueDate: '2026-10-01', time: null, category: 'ALTRO' }],
      perClass: null,
    };
    expect(resolveForClass(plain, 'c5bia')).toEqual({
      badge: 'POTENTIAL',
      summary: 'Corso pomeridiano.',
      deadlines: plain.deadlines,
    });
  });
});
