CREATE TABLE `knowledge_suggestion` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    `user_id`               BIGINT      NOT NULL COMMENT '用户ID',
    `knowledge_id`          BIGINT      NOT NULL COMMENT '对应知识点ID',
    `suggestion_type`       TINYINT     NOT NULL COMMENT '建议类型 1内容补全 2漏洞标注 3时效更新',
    `suggestion_title`      VARCHAR(128) NOT NULL COMMENT '建议标题',
    `suggestion_content`    TEXT        NOT NULL COMMENT 'AI建议的补全内容',
    `suggestion_reason`     TEXT                     COMMENT '建议原因说明',
    `status`                TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0待确认 1已采纳 2已拒绝',
    `is_deleted`            TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_status` (`status`),
    UNIQUE KEY `uk_user_knowledge_pending` (`user_id`, `knowledge_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识补全建议表';
