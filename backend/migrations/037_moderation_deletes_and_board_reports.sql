-- Richieste di eliminazione con firma (2 Rappresentanti + 1 Guardia) per Bacheca e Revisione tra pere,
-- più segnalazioni in Bacheca.
CREATE TABLE IF NOT EXISTS content_delete_requests (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    scope TEXT NOT NULL CHECK (scope IN ('BOARD_PROPOSAL', 'BOARD_COMMENT', 'FORUM_THREAD', 'FORUM_POST')),
    target_id TEXT NOT NULL,
    target_preview TEXT NOT NULL DEFAULT '',
    requested_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_delete_requests_class ON content_delete_requests(class_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS content_delete_approvals (
    request_id TEXT NOT NULL REFERENCES content_delete_requests(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('REPRESENTATIVE', 'SECURITY_GUARD')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id, user_id)
);

CREATE TABLE IF NOT EXISTS proposal_reports (
    id TEXT PRIMARY KEY,
    class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    proposal_id TEXT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    comment_id TEXT REFERENCES proposal_comments(id) ON DELETE CASCADE,
    reporter_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_proposal_reports_class ON proposal_reports(class_id, status, created_at DESC);
