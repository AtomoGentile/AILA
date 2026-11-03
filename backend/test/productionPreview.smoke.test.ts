import { execFileSync } from 'node:child_process';
import { expect, it } from 'vitest';
import { readPollPdfData, buildPollPdf } from '../src/services/pollPdf';
import { previewCalendarSync } from '../src/services/pollCalendar';
import { PDFDocument } from 'pdf-lib';
import type { Env } from '../src/types';

it.skipIf(process.env.READ_ONLY_PRODUCTION_SMOKE !== '1')('calendario e PDF beta 5 sul database reale, senza scritture', async () => {
  function statement(sql: string, bindings: unknown[] = []) {
    if (!sql.trimStart().startsWith('SELECT ') || sql.includes(';')) throw new Error('Il controllo permette soltanto SELECT singole');
    return {
      bind(...args: unknown[]) { return statement(sql, args); },
      async first() {
        let index = 0;
        const expanded = sql.replace(/\?/g, () => {
          const value = bindings[index++];
          if (typeof value !== 'string') throw new Error('Parametro non testuale nel controllo');
          return `'${value.replace(/'/g, "''")}'`;
        });
        const output = execFileSync('npx', ['wrangler', 'd1', 'execute', 'circolare_d1', '--remote', '--command', expanded, '--json'], { encoding: 'utf8', timeout: 60000, maxBuffer: 5 * 1024 * 1024 });
        let batches;
        try { batches = JSON.parse(output); } catch { throw new Error('Risposta D1 non interpretabile'); }
        if (!batches[0]?.success) throw new Error('La lettura D1 non è riuscita');
        return batches[0].results[0] ?? null;
      }
    };
  }
  const database = { prepare: statement };
  const grid = await database.prepare(`SELECT g.id, g.class_id FROM interrogation_grids g
    WHERE g.subject = 'Biologia' AND EXISTS(SELECT 1 FROM interrogation_assignments a
      JOIN interrogation_slots s ON s.id = a.slot_id WHERE s.grid_id = g.id)
    ORDER BY g.created_at DESC LIMIT 1`).first();
  expect(Boolean(grid)).toBe(true);
  const data = await readPollPdfData({ DB: database } as unknown as Env, grid.id, grid.class_id);
  expect(data).not.toBeNull();
  const students = data!.slots.reduce((sum, slot) => sum + slot.students.length, 0);
  expect(students).toBeGreaterThan(0);
  const bytes = await buildPollPdf(data!);
  const pdf = await PDFDocument.load(bytes);
  expect(pdf.getPageCount()).toBeGreaterThan(0);
  const calendar = await previewCalendarSync({ DB: database } as unknown as Env, grid.id, grid.class_id, 'read-only-check');
  expect(calendar?.syncToken).toMatch(/^[a-f0-9]{64}$/);
  expect(calendar?.dates.length).toBeGreaterThan(0);
  console.log(JSON.stringify({ check: 'production-calendar-pdf-read-only', students,
    dates: data!.slots.length, pages: pdf.getPageCount(), bytes: bytes.length,
    previousEvents: calendar?.previousEvents.length, linkedEvents: calendar?.linkedCount }));
}, 120000);
