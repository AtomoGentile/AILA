-- =============================================================================
-- AILA — Migrazione 005: esito delle proposte, commenti anonimi, quorum di svelamento
-- =============================================================================
--
-- 1. `outcome`: una proposta chiusa ora è ACCETTATA o RIFIUTATA. Lo stato resta 'CHIUSA' (il
--    CHECK su `status` non si può alterare in SQLite senza ricreare la tabella, e ricrearla
--    porterebbe via voti e commenti con il CASCADE): l'esito è una colonna a parte. Le proposte
--    già chiuse restano con outcome NULL, cioè "chiusa senza esito".
-- 2. `is_anonymous` sui commenti: ogni commento può essere scritto in forma anonima.
-- 3. Richieste di svelamento con approvazioni: il quorum della specifica (sez. 3.3) è
--    2 Rappresentanti + 1 Guardia di Sicurezza, ciascuno con la propria approvazione. Prima
--    l'endpoint accettava tre id scritti da una sola persona, quindi non autorizzava nulla.
--
-- 4. `classes.security_guard_id`: la Guardia di Sicurezza (terza firma) la sceglie il Rappresentante
--    nella Scheda Classe. È una designazione sulla classe e non un ruolo: i sondaggi, le preferenze e
--    le valutazioni filtrano su STUDENT/REPRESENTATIVE, quindi cambiare il ruolo al compagno scelto
--    lo avrebbe escluso da tutto questo.
--
-- Da usare su un database D1 GIÀ POPOLATO:
--   wrangler d1 execute <NOME_DB> --remote --file=./migrations/005_bacheca_anonimato.sql
-- (se risponde "Authentication error", esegui le istruzioni una per volta con --command, come
-- fa 001_multiclasse.ps1)
--
-- Le ALTER TABLE non sono ripetibili: al secondo giro "duplicate column name" è innocuo.

ALTER TABLE proposals ADD COLUMN outcome TEXT CHECK(outcome IN ('ACCETTATA', 'RIFIUTATA'));
ALTER TABLE proposal_comments ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE classes ADD COLUMN security_guard_id TEXT;

CREATE TABLE IF NOT EXISTS anonymity_unlock_requests (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id),
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    -- NULL = si chiede l'autore della proposta; valorizzato = si chiede l'autore del commento.
    comment_id TEXT REFERENCES proposal_comments(id) ON DELETE CASCADE,
    requested_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PENDING', 'APPROVED', 'REJECTED')) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_unlock_requests_class ON anonymity_unlock_requests(class_id, status);
CREATE INDEX IF NOT EXISTS idx_unlock_requests_proposal ON anonymity_unlock_requests(proposal_id);

CREATE TABLE IF NOT EXISTS anonymity_unlock_approvals (
    request_id TEXT NOT NULL REFERENCES anonymity_unlock_requests(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK(role IN ('REPRESENTATIVE', 'SECURITY_GUARD')),
    approved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, user_id)
);
