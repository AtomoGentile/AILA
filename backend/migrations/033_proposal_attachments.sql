ALTER TABLE content_attachments ADD COLUMN proposal_id TEXT REFERENCES proposals(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_content_attachment_proposal ON content_attachments(proposal_id) WHERE proposal_id IS NOT NULL;
