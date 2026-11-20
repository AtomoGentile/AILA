CREATE TABLE IF NOT EXISTS bureaucracy_practices (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_bureaucracy_class ON bureaucracy_practices(class_id, archived_at, created_at);

CREATE TABLE IF NOT EXISTS bureaucracy_deliveries (
    practice_id TEXT NOT NULL REFERENCES bureaucracy_practices(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    received INTEGER NOT NULL CHECK(received IN (0, 1)),
    PRIMARY KEY (practice_id, student_id)
);
