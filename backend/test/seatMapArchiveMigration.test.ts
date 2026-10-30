import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { createD1 } from './d1shim';

it('la migrazione 025 conserva tutti i dati e rimuove soltanto il tetto di quattro mappe', () => {
  const { db } = createD1();
  try {
    // Ricostruisce lo schema precedente, con il vincolo che impediva la quinta pubblicazione.
    db.exec(`DROP TABLE seat_map_history;
      CREATE TABLE seat_map_history (
        id TEXT PRIMARY KEY,
        map_index INTEGER NOT NULL CHECK(map_index BETWEEN 1 AND 4),
        layout_json TEXT NOT NULL,
        class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
        published_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );
      CREATE INDEX idx_seatmap_class ON seat_map_history(class_id);`);
    const insert = db.prepare('INSERT INTO seat_map_history VALUES (?, ?, ?, ?, ?)');
    for (let i = 1; i <= 4; i++) insert.run(`map-${i}`, i, JSON.stringify([{ row: i, column: 2 }]), 'DEFAULT_CLASS', '2026-10-30 12:30:00');
    const before = db.prepare('SELECT * FROM seat_map_history ORDER BY map_index').all();
    expect(() => db.exec('UPDATE seat_map_history SET map_index = map_index + 1')).toThrow();
    db.exec(readFileSync(new URL('../migrations/025_seat_map_archive.sql', import.meta.url), 'utf8'));
    expect(db.prepare('SELECT * FROM seat_map_history ORDER BY map_index').all()).toEqual(before);
    db.exec('UPDATE seat_map_history SET map_index = map_index + 1');
    insert.run('map-new', 1, '[]', 'DEFAULT_CLASS', '2026-11-01 12:30:00');
    expect(db.prepare('SELECT COUNT(*) AS n FROM seat_map_history').get()?.n).toBe(5);
    expect(db.prepare('PRAGMA foreign_key_check').all()).toEqual([]);
    expect(() => insert.run('invalid', 0, '[]', 'DEFAULT_CLASS', '2026-11-01')).toThrow();
  } finally {
    db.close();
  }
});
