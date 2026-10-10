import { describe, expect, it } from 'vitest';
import type { GitaCorpusDoc } from '@worker/contracts';
import { buildGitaPrompt, formatUploadDay, parseGitaAnswer, selectPassages } from '../src/ai/gita';

const pullman: GitaCorpusDoc = {
  itemId: 'g1',
  kind: 'DOCUMENT',
  category: 'PREVENTIVO',
  title: 'Preventivo pullman',
  uploadedAt: '2026-10-03 09:15:00',
  versionNo: 2,
  url: null,
  text: 'Quota di partecipazione 85 euro.\n\nSaldo entro il 10 novembre.\n\nPartenza ore 7:30 da piazza.',
};

const programma: GitaCorpusDoc = {
  itemId: 'g2',
  kind: 'DOCUMENT',
  category: 'PROGRAMMA',
  title: 'Programma',
  uploadedAt: '2026-10-08 12:00:00',
  versionNo: 1,
  url: null,
  text: 'Giorno 1: museo. Giorno 2: lago.',
};

describe('gita: date', () => {
  it('sposta la data nel formato italiano senza interpretarla', () => {
    expect(formatUploadDay('2026-10-03 09:15:00')).toBe('03/10/2026');
    expect(formatUploadDay('non una data')).toBe('non una data');
  });
});

describe('gita: passaggi', () => {
  it('sotto il budget il testo entra intero', () => {
    expect(selectPassages(pullman.text, 'quanto costa', 10_000)).toBe(pullman.text);
  });

  it('sopra il budget tiene l\'inizio e i paragrafi che rispondono alla domanda', () => {
    const long = Array.from({ length: 200 }, (_, i) => `Paragrafo ${i} sulle aule e sulla mensa scolastica.`).join('\n\n')
      + '\n\nSaldo entro il 10 novembre con bonifico.';
    const picked = selectPassages(long, 'entro quando devo pagare il saldo', 800);
    expect(picked.length).toBeLessThanOrEqual(800);
    expect(picked).toContain('Saldo entro il 10 novembre');
  });
});

describe('gita: prompt', () => {
  it('contiene le regole della modalità solo materiale, il materiale e la domanda', () => {
    const prompt = buildGitaPrompt('quanto costa la gita', [pullman], '2026-10-10');
    expect(prompt).toContain('MODALITA\' SOLO MATERIALE');
    expect(prompt).toContain('Non lo trovo nel materiale disponibile');
    expect(prompt).toContain('--- Preventivo pullman (preventivo, caricato 03/10/2026) ---');
    expect(prompt).toContain('85 euro');
    expect(prompt).toContain('DOMANDA: quanto costa la gita');
  });

  it('senza materiale lo dice invece di inventarlo', () => {
    expect(buildGitaPrompt('ciao', [], '2026-10-10')).toContain('nessun materiale caricato');
  });
});

describe('gita: risposta', () => {
  it('cita solo documenti che esistono nel materiale, con la data vera', () => {
    const raw = JSON.stringify({
      answer: 'La quota è 85 euro.',
      sources: ['Preventivo pullman', 'Circolare inventata del 2019'],
    });
    const parsed = parseGitaAnswer(raw, [pullman, programma]);
    expect(parsed.text).toBe('La quota è 85 euro.');
    expect(parsed.sources).toEqual([{ title: 'Preventivo pullman', uploadedOn: '03/10/2026' }]);
  });

  it('se il modello non risponde in JSON usa il testo così com\'è', () => {
    const parsed = parseGitaAnswer('Non lo trovo nel materiale disponibile.', [pullman]);
    expect(parsed.text).toBe('Non lo trovo nel materiale disponibile.');
    expect(parsed.sources).toEqual([]);
  });
});
