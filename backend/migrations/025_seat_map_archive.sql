-- Rimuove il vecchio limite di quattro pubblicazioni senza perdere lo storico.
-- Le proposte non pubblicate non sono mai inserite in questa tabella.
CREATE TABLE seat_map_history_archive (
    id TEXT PRIMARY KEY,
    map_index INTEGER NOT NULL CHECK(map_index >= 1),
    layout_json TEXT NOT NULL,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    published_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO seat_map_history_archive (id, map_index, layout_json, class_id, published_at)
SELECT id, map_index, layout_json, class_id, published_at FROM seat_map_history;

DROP TABLE seat_map_history;
ALTER TABLE seat_map_history_archive RENAME TO seat_map_history;
CREATE INDEX idx_seatmap_class ON seat_map_history(class_id);
