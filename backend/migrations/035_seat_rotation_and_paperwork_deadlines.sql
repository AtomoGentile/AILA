ALTER TABLE classes ADD COLUMN seat_row_cycle INTEGER CHECK (seat_row_cycle BETWEEN 1 AND 30);
ALTER TABLE classes ADD COLUMN seat_companion_wait INTEGER NOT NULL DEFAULT 4 CHECK (seat_companion_wait BETWEEN 0 AND 30);
ALTER TABLE bureaucracy_practices ADD COLUMN due_date TEXT;
ALTER TABLE classes ADD COLUMN seat_rotation_revision INTEGER NOT NULL DEFAULT 0;

CREATE TRIGGER IF NOT EXISTS seat_rotation_history_insert AFTER INSERT ON seat_map_history
BEGIN
    UPDATE classes SET seat_rotation_revision = seat_rotation_revision + 1 WHERE id = NEW.class_id;
END;

CREATE TRIGGER IF NOT EXISTS seat_rotation_history_update AFTER UPDATE OF layout_json, published_at ON seat_map_history
BEGIN
    UPDATE classes SET seat_rotation_revision = seat_rotation_revision + 1 WHERE id = NEW.class_id;
END;

CREATE TRIGGER IF NOT EXISTS seat_rotation_history_delete AFTER DELETE ON seat_map_history
BEGIN
    UPDATE classes SET seat_rotation_revision = seat_rotation_revision + 1 WHERE id = OLD.class_id;
END;
