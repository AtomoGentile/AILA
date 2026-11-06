-- Per-user dismissal of a specific revision; a new reply or changed source can resurface.
CREATE TABLE IF NOT EXISTS aca_brief_dismissals (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 item_id TEXT NOT NULL,
 revision TEXT NOT NULL,
 expires_at TEXT NOT NULL,
 PRIMARY KEY(user_id,item_id,revision)
);
