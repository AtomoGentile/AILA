-- Aggiunge ENTRATA al CHECK SQLite senza perdere gli eventi esistenti.
CREATE TABLE calendar_events_new (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    event_date DATE NOT NULL,
    start_time TEXT,
    category TEXT NOT NULL CHECK(category IN ('VERIFICA', 'INTERROGAZIONE', 'PAGAMENTO', 'USCITA_DIDATTICA', 'ENTRATA', 'AVVISO', 'ALTRO')),
    is_for_all BOOLEAN NOT NULL DEFAULT 1,
    is_ai_generated BOOLEAN NOT NULL DEFAULT 0,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    class_id TEXT NOT NULL DEFAULT 'DEFAULT_CLASS' REFERENCES classes(id),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    -- Array JSON di id utente (es. '["u1","u2"]'), NULL quando is_for_all = 1.
    visible_to_user_ids_json TEXT,
    -- Circolare da cui l'ha ricavato AILA Assistant (migrazione 015).
    circular_number INTEGER
);



INSERT INTO calendar_events_new (id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,created_at,notes,visible_to_user_ids_json,circular_number) SELECT id,title,event_date,start_time,category,is_for_all,is_ai_generated,created_by,class_id,created_at,notes,visible_to_user_ids_json,circular_number FROM calendar_events;
DROP TABLE calendar_events;
ALTER TABLE calendar_events_new RENAME TO calendar_events;
CREATE INDEX idx_calendar_class ON calendar_events(class_id);
