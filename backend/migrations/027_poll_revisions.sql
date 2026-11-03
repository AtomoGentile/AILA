-- Revisioni esplicite: la fotografia del calcolo iniziale rimane immutabile.
ALTER TABLE calendar_events ADD COLUMN poll_grid_id TEXT REFERENCES interrogation_grids(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_calendar_poll_grid ON calendar_events(poll_grid_id);
CREATE TABLE IF NOT EXISTS interrogation_revisions (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
  basis TEXT NOT NULL,
  bonus_policy TEXT NOT NULL,
  before_state TEXT CHECK(before_state IS NULL OR json_valid(before_state)),
  after_state TEXT CHECK(after_state IS NULL OR json_valid(after_state)),
  created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  restore_run_id TEXT,
  restored_at DATETIME
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_interrogation_active_revision ON interrogation_revisions(grid_id) WHERE restored_at IS NULL;
-- Rimuove anche nomi, voti e bonus dalle fotografie di revisione dopo la cancellazione.
CREATE TRIGGER IF NOT EXISTS redact_interrogation_revisions BEFORE DELETE ON users
BEGIN
  UPDATE interrogation_revisions SET before_state = NULL, after_state = NULL
  WHERE grid_id IN (SELECT id FROM interrogation_grids WHERE class_id = OLD.class_id);
END;
