-- =============================================================================
-- AILA — Migrazione 018: sezione Gita (documenti, link, versioni, segnalazioni)
-- =============================================================================
--
-- Documenti e link della gita della classe. Ogni modifica crea una nuova versione numerata: la
-- precedente resta consultabile, lo storico qui e' obbligatorio. Il ritiro di una voce e' logico
-- (withdrawn = 1): nulla viene cancellato, il rappresentante puo' ancora vedere lo storico.
--
-- Il file PDF e il testo estratto stanno su R2 (chiavi in gita_versions), non in D1: un testo
-- puo' arrivare a 100 MB, troppo per una riga di D1.
--
-- Solo CREATE ... IF NOT EXISTS: si puo' rilanciare senza errori.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/018_gita.sql

CREATE TABLE IF NOT EXISTS gita_items (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    kind TEXT NOT NULL CHECK(kind IN ('DOCUMENT', 'LINK')),
    category TEXT NOT NULL DEFAULT 'ALTRO',
    title TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    withdrawn INTEGER NOT NULL DEFAULT 0,
    withdrawn_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_gita_items_class ON gita_items(class_id);

CREATE TABLE IF NOT EXISTS gita_versions (
    id TEXT PRIMARY KEY,
    item_id TEXT NOT NULL REFERENCES gita_items(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    uploaded_by TEXT NOT NULL,
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    file_r2_key TEXT,
    file_mime TEXT,
    file_size INTEGER,
    text_r2_key TEXT,
    text_chars INTEGER NOT NULL DEFAULT 0,
    url TEXT,
    note TEXT,
    UNIQUE(item_id, version_no)
);

CREATE TABLE IF NOT EXISTS gita_reports (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    item_id TEXT NOT NULL REFERENCES gita_items(id) ON DELETE CASCADE,
    version_id TEXT,
    reported_by TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN', 'RESOLVED')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    resolved_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_gita_reports_class ON gita_reports(class_id);
CREATE INDEX IF NOT EXISTS idx_gita_reports_reporter ON gita_reports(reported_by);
