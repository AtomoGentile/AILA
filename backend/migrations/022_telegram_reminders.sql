-- =============================================================================
-- CIRCOLARE+ / AILA — Migrazione 022: Promemoria Telegram per la firma iOS
-- =============================================================================

ALTER TABLE users ADD COLUMN signature_expires_at TEXT;

CREATE TABLE IF NOT EXISTS telegram_links (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    chat_id TEXT NOT NULL UNIQUE,
    linked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS telegram_signature_reminders (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signature_expires_at TEXT NOT NULL,
    sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, signature_expires_at)
);

CREATE TABLE IF NOT EXISTS telegram_link_codes (
    code TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME
);
