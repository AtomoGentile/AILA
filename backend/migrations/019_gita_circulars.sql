-- =============================================================================
-- AILA — Migrazione 019: circolari della gita, per classe
-- =============================================================================
--
-- Le circolari sono della scuola, ma quelle che riguardano la gita sono di ogni classe: questa
-- tabella dice quali circolari il Rappresentante di una classe ha aggiunto alla sezione Gita.
-- L'elenco mostrato è l'unione di queste e di quelle il cui titolo parla di gita o uscita.
--
-- Solo CREATE ... IF NOT EXISTS: si puo' rilanciare senza errori.
--
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/019_gita_circulars.sql

CREATE TABLE IF NOT EXISTS gita_circulars (
    class_id TEXT NOT NULL REFERENCES classes(id),
    circular_number INTEGER NOT NULL REFERENCES circulars(number) ON DELETE CASCADE,
    added_by TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, circular_number)
);
