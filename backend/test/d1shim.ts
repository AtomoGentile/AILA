// D1 finto per i test: lo stesso SQLite, dentro Node (node:sqlite), con lo schema reale.
// Copre solo quello che usano le rotte: prepare/bind/first/all/run e batch.
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';

type Value = string | number | bigint | null | Uint8Array;

function toSqlite(v: unknown): Value {
  if (v === undefined || v === null) return null;
  if (typeof v === 'boolean') return v ? 1 : 0;
  return v as Value;
}

class Statement {
  constructor(private db: DatabaseSync, private sql: string, private args: Value[] = []) {}

  bind(...args: unknown[]): Statement {
    return new Statement(this.db, this.sql, args.map(toSqlite));
  }

  async first<T>(column?: string): Promise<T | null> {
    const row = this.db.prepare(this.sql).get(...this.args) as Record<string, unknown> | undefined;
    if (!row) return null;
    const plain = { ...row };
    return (column ? plain[column] : plain) as T;
  }

  async all<T>(): Promise<{ results: T[]; success: true }> {
    const rows = this.db.prepare(this.sql).all(...this.args) as Record<string, unknown>[];
    return { results: rows.map((r) => ({ ...r }) as T), success: true };
  }

  runSync() {
    const info = this.db.prepare(this.sql).run(...this.args);
    return { success: true, meta: { changes: Number(info.changes), last_row_id: Number(info.lastInsertRowid) } };
  }

  async run() {
    return this.runSync();
  }
}

export function createD1(): { db: DatabaseSync; d1: D1Database } {
  const db = new DatabaseSync(':memory:');
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const d1 = {
    prepare: (sql: string) => new Statement(db, sql),
    batch: async (stmts: Statement[]) => {
      db.exec('BEGIN');
      try {
        const out = stmts.map((s) => s.runSync());
        db.exec('COMMIT');
        return out;
      } catch (e) {
        db.exec('ROLLBACK');
        throw e;
      }
    },
  };
  return { db, d1: d1 as unknown as D1Database };
}
