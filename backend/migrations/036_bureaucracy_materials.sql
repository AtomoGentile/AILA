CREATE TABLE IF NOT EXISTS bureaucracy_materials (
    id TEXT PRIMARY KEY,
    practice_id TEXT NOT NULL REFERENCES bureaucracy_practices(id) ON DELETE CASCADE,
    file_name TEXT,
    circular_number INTEGER REFERENCES circulars(number) ON DELETE CASCADE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((file_name IS NULL) <> (circular_number IS NULL)),
    UNIQUE (practice_id, circular_number)
);

CREATE INDEX IF NOT EXISTS idx_bureaucracy_materials_practice ON bureaucracy_materials(practice_id, created_at, id);
