-- =============================================================================
-- AILA — Migrazione 002: cache condivisa dell'analisi AI delle circolari
-- =============================================================================
--
-- Prima l'analisi AI (badge, riassunto, scadenze) girava solo sul telefono e restava in memoria:
-- si perdeva ad ogni riavvio dell'app e ogni studente la rifaceva per conto proprio, anche per
-- una circolare già analizzata da un compagno pochi minuti prima.
--
-- Da usare su un database D1 già popolato:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/002_circular_ai_analysis.sql
--
-- È scritta per poter essere rieseguita: CREATE TABLE è IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS circular_ai_analysis (
    circular_number INTEGER PRIMARY KEY REFERENCES circulars(number) ON DELETE CASCADE,
    badge TEXT NOT NULL CHECK(badge IN ('RELEVANT', 'POTENTIAL', 'NOT_RELEVANT')),
    summary TEXT NOT NULL,
    deadlines_json TEXT NOT NULL DEFAULT '[]',
    is_fallback BOOLEAN NOT NULL DEFAULT 0,
    model_label TEXT NOT NULL DEFAULT 'Sconosciuto',
    submitted_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
