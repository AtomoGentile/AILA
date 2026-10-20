-- ============================================================================= 
-- CIRCOLARE+ — Migrazione 021: fantocci della Mappa Posti
-- =============================================================================
--
-- Studenti inventati dal Rappresentante, utilizzati solo per la disposizione dei
-- banchi: occupano un posto come tutti ma di fatto non esistono. Non sono righe
-- di `users` (nessuna credenziale, nessun class_code valido per loro), quindi
-- di diritto non votano le preferenze sociali; la rotta di voto li respinge
-- anche come destinatario, cosi' nessuno puo' votare un fantoccio.
--
-- E' rieseguibile (CREATE TABLE IF NOT EXISTS):
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/021_class_dummies.sql
--
CREATE TABLE IF NOT EXISTS class_dummies (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dummies_class ON class_dummies(class_id);
