-- Claim per sincronizzazioni atomiche del calendario, senza copie di dati personali.
CREATE TABLE IF NOT EXISTS interrogation_calendar_syncs (
  id TEXT PRIMARY KEY,
  grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_interrogation_calendar_syncs_grid ON interrogation_calendar_syncs(grid_id);
