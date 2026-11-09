-- Più gite per classe; i materiali precedenti restano nella prima gita.
CREATE TABLE IF NOT EXISTS gita_trips (
 id TEXT PRIMARY KEY, class_id TEXT NOT NULL REFERENCES classes(id),
 name TEXT NOT NULL, created_by TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_gita_trips_class ON gita_trips(class_id);
INSERT OR IGNORE INTO gita_trips (id, class_id, name, created_by, created_at)
 SELECT 'default:' || id, id, 'Gita 1', '', CURRENT_TIMESTAMP FROM classes;
ALTER TABLE gita_items ADD COLUMN trip_id TEXT REFERENCES gita_trips(id);
UPDATE gita_items SET trip_id = 'default:' || class_id;
CREATE INDEX IF NOT EXISTS idx_gita_items_trip ON gita_items(trip_id);
ALTER TABLE gita_circulars RENAME TO gita_circulars_legacy;
CREATE TABLE gita_circulars (
 class_id TEXT NOT NULL REFERENCES classes(id),
 trip_id TEXT NOT NULL REFERENCES gita_trips(id),
 circular_number INTEGER NOT NULL REFERENCES circulars(number) ON DELETE CASCADE,
 added_by TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (trip_id, circular_number)
);
INSERT INTO gita_circulars (class_id, trip_id, circular_number, added_by, created_at)
 SELECT class_id, 'default:' || class_id, circular_number, added_by, created_at FROM gita_circulars_legacy;
DROP TABLE gita_circulars_legacy;
CREATE TABLE IF NOT EXISTS gita_updates (
 id TEXT PRIMARY KEY, trip_id TEXT NOT NULL REFERENCES gita_trips(id),
 title TEXT NOT NULL, body TEXT NOT NULL, author_id TEXT NOT NULL,
 author_name TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_gita_updates_trip ON gita_updates(trip_id, created_at);
