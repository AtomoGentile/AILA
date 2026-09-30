-- =============================================================================
-- AILA — Migrazione 013: tentativi di accesso, codici classe, reset password, indici
-- =============================================================================
--
-- Solo CREATE ... IF NOT EXISTS: si puo' rilanciare senza errori. Le rotte funzionano anche
-- prima che sia applicata (senza queste tabelle i controlli nuovi si saltano), ma i limiti ai
-- tentativi, i codici classe e il reset della password partono solo dopo.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/013_security_and_indexes.sql

-- Tentativi di login/registrazione/reset in una finestra di tempo (vedi src/rateLimit.ts).
-- `window_start` in secondi epoch. Le righe vecchie le cancella il cron.
CREATE TABLE IF NOT EXISTS auth_attempts (
    key TEXT PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 0,
    window_start INTEGER NOT NULL
);

-- Codice per entrare in una classe che ha gia' iscritti: lo vede il Rappresentante nella Scheda
-- Classe e lo gira ai compagni. Senza, chiunque poteva registrarsi in qualunque classe e vederne
-- nomi, bacheca e calendario.
CREATE TABLE IF NOT EXISTS class_invites (
    class_id TEXT PRIMARY KEY REFERENCES classes(id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Codice monouso per reimpostare la password, generato dal Rappresentante per un compagno
-- (niente email nell'app). Si salva solo l'hash.
CREATE TABLE IF NOT EXISTS password_reset_codes (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_by TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Indici per le join piu' frequenti (sondaggi, commenti, token push).
CREATE INDEX IF NOT EXISTS idx_slots_grid ON interrogation_slots(grid_id);
CREATE INDEX IF NOT EXISTS idx_comments_proposal ON proposal_comments(proposal_id);
CREATE INDEX IF NOT EXISTS idx_fcm_token ON fcm_tokens(token);
