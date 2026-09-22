-- =============================================================================
-- AILA — Migrazione 007: coppie da separare per disciplina
-- =============================================================================
--
-- Il voto di comportamento (1-5) e' per persona: non sa dire "questi due insieme fanno caos, con
-- altri sono tranquilli". Il Rappresentante segna qui la coppia, con una scadenza, e
-- l'ottimizzatore della Mappa Posti penalizza solo quel banco (non lo vieta: la rotazione mensile
-- deve poter variare). Riservato ai Rappresentanti, come le valutazioni.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/007_discipline_pairs.sql

CREATE TABLE IF NOT EXISTS discipline_pairs (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL,
    -- Sempre in ordine (student_a < student_b), cosi' la stessa coppia non compare due volte.
    student_a TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_b TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at DATE NOT NULL,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(class_id, student_a, student_b)
);
