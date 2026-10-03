-- =============================================================================
-- AILA — Migrazione 017: codici Rappresentante per classe e monouso
-- =============================================================================
--
-- Prima il codice Rappresentante era uno per tutta la scuola (secret REPRESENTATIVE_SIGNUP_CODE):
-- chi lo conosceva, insieme al codice classe, poteva registrare da solo un secondo account
-- Rappresentante nella propria classe e, nominando la Guardia, avere tutte e tre le firme del
-- quorum che svela gli anonimi. Ora ogni codice vale per una classe sola e una volta sola; lo
-- emette chi ha ADMIN_SECRET (POST /api/admin/representative-invites, vedi README), mai un
-- Rappresentante.
--
-- Solo CREATE ... IF NOT EXISTS: si puo' rilanciare senza errori. Senza questa tabella la
-- registrazione come Rappresentante risponde 503 (resta solo il codice globale di transizione).
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/017_representative_invites.sql

-- Si salva solo l'hash SHA-256 del codice, come in password_reset_codes. `class_id` senza
-- REFERENCES: il codice si puo' emettere per una classe che nascera' con la registrazione del suo
-- primo Rappresentante. `used_by` senza REFERENCES: la riga resta come traccia anche se l'account
-- viene eliminato. `expires_at` in secondi epoch.
CREATE TABLE IF NOT EXISTS representative_invites (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL,
    code_hash TEXT NOT NULL UNIQUE,
    expires_at INTEGER NOT NULL,
    used_by TEXT,
    used_at INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_representative_invites_class ON representative_invites(class_id);
