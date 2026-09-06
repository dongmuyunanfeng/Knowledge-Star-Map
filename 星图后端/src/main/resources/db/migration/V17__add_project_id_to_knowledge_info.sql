ALTER TABLE knowledge_info
  ADD COLUMN project_id BIGINT NULL COMMENT '所属项目ID，普通知识点为NULL',
  ADD KEY idx_project_id (project_id);
