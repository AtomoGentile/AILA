import { execFileSync } from 'node:child_process';
import { expect, it } from 'vitest';
import { previewAssignments } from '../src/services/pollPreview';
import type { Env } from '../src/types';

it.skipIf(process.env.READ_ONLY_PRODUCTION_SMOKE !== '1')('anteprima beta 2 sul database reale, senza scritture', async () => {
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
  const result = await previewAssignments({ DB: database } as unknown as Env, grid.id, grid.class_id);
  expect(result?.basis).toBe('VOTES_ONLY');
  expect(result?.totalStudents).toBeGreaterThan(0);
  expect(result?.assignments.length).toBe(result?.totalStudents);
  console.log(JSON.stringify({ check: 'production-preview-read-only', basis: result?.basis,
    students: result?.totalStudents, moved: result?.moved, improved: result?.improved, worsened: result?.worsened,
    before: result?.before, after: result?.after }));
}, 120000);
