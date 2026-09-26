-- =============================================================================
-- AILA — Migrazione 010: iscrizioni Web Push della PWA
-- =============================================================================
--
-- Solo additiva: una tabella nuova, nessuna colonna esistente toccata. Le iscrizioni sono
-- legate all'utente e spariscono con lui (ON DELETE CASCADE). L'endpoint identifica il browser:
-- chi accede per ultimo su quel browser ne diventa il proprietario (come per fcm_tokens).
--
--   wrangler d1 execute circolare_d1 --remote --file=./migrations/010_web_push.sql

CREATE TABLE IF NOT EXISTS web_push_subscriptions (
    endpoint    TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    -- Categorie silenziate, separate da virgola ("circulars,board"). Vuoto = tutte attive.
    muted_kinds TEXT NOT NULL DEFAULT '',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_push_user ON web_push_subscriptions(user_id);
