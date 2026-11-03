-- Chiusura atomica e fotografia del calcolo; non modifica calendari o bonus esistenti.
CREATE TABLE IF NOT EXISTS interrogation_calculations (
  grid_id TEXT PRIMARY KEY REFERENCES interrogation_grids(id) ON DELETE CASCADE,
  run_id TEXT NOT NULL UNIQUE,
  algorithm_version TEXT NOT NULL,
  input_json TEXT CHECK(input_json IS NULL OR json_valid(input_json)),
  result_json TEXT CHECK(result_json IS NULL OR json_valid(result_json)),
  rules_json TEXT CHECK(rules_json IS NULL OR json_valid(rules_json)),
  redacted_students INTEGER NOT NULL DEFAULT 0,
  calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- I risultati precedenti restano chiusi anche se in seguito si eliminano tutti gli account.
-- Non inventa voti o bonus iniziali che non erano stati salvati.
INSERT OR IGNORE INTO interrogation_calculations (grid_id, run_id, algorithm_version, calculated_at)
SELECT s.grid_id, 'legacy:' || s.grid_id, 'legacy', MIN(a.assigned_at)
FROM interrogation_assignments a JOIN interrogation_slots s ON s.id = a.slot_id GROUP BY s.grid_id;

CREATE TABLE IF NOT EXISTS interrogation_swap_events (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  request_id TEXT UNIQUE REFERENCES swap_requests(id) ON DELETE SET NULL,
  grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE,
  requester_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  requester_slot_id TEXT NOT NULL REFERENCES interrogation_slots(id) ON DELETE CASCADE,
  target_slot_id TEXT NOT NULL REFERENCES interrogation_slots(id) ON DELETE CASCADE,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_interrogation_swap_events_grid ON interrogation_swap_events(grid_id);

-- La cancellazione dell'account elimina anche voti e bonus dalla fotografia del calcolo.
CREATE TRIGGER IF NOT EXISTS redact_interrogation_calculations
BEFORE DELETE ON users
BEGIN
  UPDATE interrogation_calculations SET
    input_json = json_remove(input_json, '$.students.' || json_quote(OLD.id)),
    result_json = json_remove(result_json, '$.' || json_quote(OLD.id)),
    redacted_students = redacted_students + 1
  WHERE json_type(input_json, '$.students.' || json_quote(OLD.id)) IS NOT NULL;
END;
