-- Educazione tra pere: class-scoped conversations, stable pseudonyms, explicit AI consent.
CREATE TABLE IF NOT EXISTS forum_threads (
 id TEXT PRIMARY KEY, class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 author_id TEXT REFERENCES users(id) ON DELETE SET NULL, title TEXT NOT NULL,
 resolved INTEGER NOT NULL DEFAULT 0 CHECK(resolved IN (0,1)), featured_post_id TEXT,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, last_message_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 nudged_at TEXT, hidden INTEGER NOT NULL DEFAULT 0 CHECK(hidden IN (0,1))
);
CREATE INDEX IF NOT EXISTS idx_forum_threads_class ON forum_threads(class_id, last_message_at DESC);
CREATE TABLE IF NOT EXISTS forum_participants (
 thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, public_key TEXT NOT NULL UNIQUE,
 ordinal INTEGER NOT NULL, anonymous INTEGER NOT NULL CHECK(anonymous IN (0,1)),
 PRIMARY KEY(thread_id,user_id), UNIQUE(thread_id,ordinal)
);
CREATE TABLE IF NOT EXISTS forum_posts (
 id TEXT PRIMARY KEY, thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
 author_id TEXT REFERENCES users(id) ON DELETE SET NULL,
 parent_id TEXT REFERENCES forum_posts(id) ON DELETE SET NULL,
 body TEXT NOT NULL, mentions_json TEXT NOT NULL DEFAULT '[]' CHECK(json_valid(mentions_json)),
 is_ai INTEGER NOT NULL DEFAULT 0, generated_at TEXT, provider TEXT,
 hidden INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_forum_posts_thread ON forum_posts(thread_id,created_at,id);
CREATE TABLE IF NOT EXISTS forum_subscriptions (
 thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 mode TEXT NOT NULL DEFAULT 'REPLIES' CHECK(mode IN ('REPLIES','ALL','NONE')),
 PRIMARY KEY(thread_id,user_id)
);
CREATE TABLE IF NOT EXISTS forum_reports (
 id TEXT PRIMARY KEY, thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
 post_id TEXT REFERENCES forum_posts(id) ON DELETE SET NULL,
 reported_by TEXT REFERENCES users(id) ON DELETE SET NULL, reason TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, resolved_at TEXT
);
CREATE TABLE IF NOT EXISTS content_attachments (
 id TEXT PRIMARY KEY, class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 uploaded_by TEXT REFERENCES users(id) ON DELETE SET NULL,
 thread_id TEXT REFERENCES forum_threads(id) ON DELETE CASCADE,
 post_id TEXT REFERENCES forum_posts(id) ON DELETE SET NULL,
 file_name TEXT NOT NULL, mime TEXT NOT NULL, byte_size INTEGER NOT NULL,
 r2_key TEXT NOT NULL UNIQUE, extracted_text TEXT NOT NULL DEFAULT '',
 transcript TEXT, transcript_generated_at TEXT, duration_seconds REAL,
 expires_at TEXT NOT NULL, deleted_at TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_content_attachment_expiry ON content_attachments(expires_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_content_attachment_thread ON content_attachments(thread_id,post_id);
CREATE TABLE IF NOT EXISTS ai_preferences (
 user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 ai_enabled INTEGER NOT NULL DEFAULT 0, aca_enabled INTEGER NOT NULL DEFAULT 0, hold_to_open INTEGER NOT NULL DEFAULT 1,
 services_json TEXT NOT NULL DEFAULT '["CIRCULARS","CHAT"]' CHECK(json_valid(services_json)),
 cloud_consent INTEGER NOT NULL DEFAULT 0, external_consent INTEGER NOT NULL DEFAULT 0,
 attachment_consent INTEGER NOT NULL DEFAULT 0, audio_consent INTEGER NOT NULL DEFAULT 0,
 nudges_enabled INTEGER NOT NULL DEFAULT 0, advanced_enabled INTEGER NOT NULL DEFAULT 0,
 updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ai_usage (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, day TEXT NOT NULL,
 feature TEXT NOT NULL, requests INTEGER NOT NULL DEFAULT 0, reserved_tokens INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,day,feature)
);
CREATE TABLE IF NOT EXISTS ai_account_budget (
 day TEXT PRIMARY KEY, reserved_tokens INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS aca_operations (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 plan_json TEXT NOT NULL CHECK(json_valid(plan_json)),
 status TEXT NOT NULL DEFAULT 'PREVIEW' CHECK(status IN ('PREVIEW','RUNNING','COMPLETED','PARTIAL','CANCELLED','EXPIRED')),
 result_json TEXT CHECK(result_json IS NULL OR json_valid(result_json)),
 shared_proposal_id TEXT, execution_token TEXT, confirmed_at TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_aca_operations_owner ON aca_operations(user_id,created_at);
CREATE TABLE IF NOT EXISTS aca_steps (
 operation_id TEXT NOT NULL REFERENCES aca_operations(id) ON DELETE CASCADE,
 step INTEGER NOT NULL, tool TEXT NOT NULL, event_id TEXT,
 status TEXT NOT NULL DEFAULT 'PENDING', PRIMARY KEY(operation_id,step)
);
CREATE TABLE IF NOT EXISTS moderation_audit (
 id TEXT PRIMARY KEY, class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 actor_id TEXT REFERENCES users(id) ON DELETE SET NULL, target_id TEXT NOT NULL,
 action TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS aca_calendar_claims (
 key TEXT PRIMARY KEY, event_id TEXT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
 event_date TEXT NOT NULL, title_key TEXT NOT NULL, category TEXT NOT NULL
);
CREATE TRIGGER IF NOT EXISTS aca_claim_event_changed
AFTER UPDATE OF title,event_date,category,visible_to_user_ids_json ON calendar_events
BEGIN
 DELETE FROM aca_calendar_claims WHERE event_id=NEW.id;
END;
CREATE TABLE IF NOT EXISTS aca_nudge_dismissals (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 event_id TEXT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
 event_date TEXT NOT NULL, PRIMARY KEY(user_id,event_id,event_date)
);

CREATE TABLE IF NOT EXISTS forum_calendar_scans (
 thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE, revision TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('RUNNING','DONE','FAILED')), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(thread_id,revision)
);
CREATE TABLE IF NOT EXISTS forum_calendar_proposals (
 id TEXT PRIMARY KEY, thread_id TEXT NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
 class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE, revision TEXT NOT NULL,
 source_ids_json TEXT NOT NULL CHECK(json_valid(source_ids_json)), plan_json TEXT NOT NULL CHECK(json_valid(plan_json)),
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED')), provider TEXT NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TEXT NOT NULL,
 accepted_at TEXT, accepted_by TEXT REFERENCES users(id) ON DELETE SET NULL, operation_id TEXT,
 UNIQUE(thread_id,revision)
);
CREATE INDEX IF NOT EXISTS idx_forum_calendar_proposals_thread ON forum_calendar_proposals(thread_id,created_at);
