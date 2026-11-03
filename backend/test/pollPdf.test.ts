import { expect, it } from 'vitest';
import { PDFDocument } from 'pdf-lib';
import { writeFileSync } from 'node:fs';
import { buildPollPdf } from '../src/services/pollPdf';

it('crea un PDF A4 su più pagine, con nomi accentati, lunghi e una data senza studenti', async () => {
  const students = Array.from({ length: 90 }, (_, i) => ({ firstName: `Éléonore ${i + 1}`, lastName: i === 0 ? 'D’Ángelo Ștefan Μάρκος Юлия' : 'Cognome molto lungo con più parti per verificare gli a capo senza troncamenti' }));
  const bytes = await buildPollPdf({ subject: 'Biologia e scienze della Terra', classLabel: '4 CSA', audience: null,
    slots: [{ date: '2026-12-03', students }, { date: '2026-12-04', students: [] }] });
  const pdf = await PDFDocument.load(bytes);
  expect(pdf.getPageCount()).toBeGreaterThanOrEqual(4);
  expect(pdf.getTitle()).toBe('Calendario interrogazioni - Biologia e scienze della Terra');
  for (const page of pdf.getPages()) expect(page.getSize()).toEqual({ width: 595, height: 842 });
  if (process.env.POLL_PDF_SAMPLE_PATH) writeFileSync(process.env.POLL_PDF_SAMPLE_PATH, bytes);
});
