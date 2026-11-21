ALTER TABLE classes ADD COLUMN seat_row_cycle INTEGER CHECK (seat_row_cycle BETWEEN 1 AND 30);
ALTER TABLE classes ADD COLUMN seat_companion_wait INTEGER NOT NULL DEFAULT 4 CHECK (seat_companion_wait BETWEEN 0 AND 30);
ALTER TABLE bureaucracy_practices ADD COLUMN due_date TEXT;
