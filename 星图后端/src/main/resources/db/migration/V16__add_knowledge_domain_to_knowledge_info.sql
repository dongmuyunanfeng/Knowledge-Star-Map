ALTER TABLE knowledge_info ADD COLUMN knowledge_domain VARCHAR(64) NOT NULL DEFAULT '' AFTER knowledge_tag;
CREATE INDEX idx_knowledge_domain ON knowledge_info(user_id, knowledge_domain, is_deleted);
