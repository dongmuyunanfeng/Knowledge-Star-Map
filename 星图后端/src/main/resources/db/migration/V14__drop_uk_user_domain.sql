-- V14 移除 knowledge_star_map.uk_user_domain 唯一索引
-- 原因：逻辑删除后无法新建同名domain（唯一约束仍生效），upsert逻辑改为查询is_deleted=0的记录
ALTER TABLE knowledge_star_map DROP INDEX uk_user_domain;
