-- Migrazione 020 — Cambio nome: richieste da approvare al Rappresentante.
--
-- Lo studente chiede (POST /api/users/me/name-request), il Rappresentante o la Guardia
-- approva/rifiuta (POST /api/users/name-requests/:id/approve|reject). La richiesta avvisa i
-- firmatari della classe (Rappresentanti + Guardia, richiedente escluso), la decisione avvisa
-- il richiedente. Nessuno decide sulla propria richiesta. Applicare a mano con il workflow
-- "Worker deploy" (input "Migrazioni D1": 020_name_change_requests.sql) prima del deploy.

CREATE TABLE IF NOT EXISTS name_change_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    old_first_name TEXT NOT NULL,
    old_last_name TEXT NOT NULL,
    new_first_name TEXT NOT NULL,
    new_last_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    decided_by TEXT,
    decided_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_name_change_requests_user ON name_change_requests(user_id);
