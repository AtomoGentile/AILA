-- =============================================================================
-- AILA — Migrazione 008: sondaggi a ordinamento
-- =============================================================================
--
-- Sondaggi in cui ognuno mette in ordine un elenco di opzioni (es. "dove andiamo in gita?"),
-- invece di votare un'opzione sola. Il risultato è una classifica a punti (metodo Borda): con
-- N opzioni, la prima di ogni classifica vale N-1 punti, l'ultima 0. I risultati sono sempre
-- aggiornati, non c'è un calcolo da far partire.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/008_ranking_polls.sql

CREATE TABLE IF NOT EXISTS ranking_polls (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    question TEXT NOT NULL,
    -- Chiuso dal Rappresentante: le classifiche non si cambiano più.
    is_closed BOOLEAN NOT NULL DEFAULT 0,
    created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ranking_polls_class ON ranking_polls(class_id);

CREATE TABLE IF NOT EXISTS ranking_poll_options (
    id TEXT PRIMARY KEY,
    poll_id TEXT NOT NULL REFERENCES ranking_polls(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    -- Ordine in cui il Rappresentante le ha scritte: è anche l'ordine di partenza per chi vota.
    position INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ranking_options_poll ON ranking_poll_options(poll_id);

-- Una riga per opzione per votante: rank 1 = la preferita.
CREATE TABLE IF NOT EXISTS ranking_poll_answers (
    poll_id TEXT NOT NULL REFERENCES ranking_polls(id) ON DELETE CASCADE,
    option_id TEXT NOT NULL REFERENCES ranking_poll_options(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL,
    PRIMARY KEY (poll_id, user_id, option_id)
);
