CREATE TABLE `knowledge_complete_log` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '补全记录ID',
    `user_id`          BIGINT     NOT NULL COMMENT '用户ID',
    `knowledge_id`     BIGINT     NOT NULL COMMENT '对应知识点ID',
    `suggestion_id`    BIGINT     DEFAULT NULL COMMENT '关联的知识建议ID',
    `old_content`      TEXT                    COMMENT '补全前原始内容快照',
    `new_content`      TEXT         NOT NULL COMMENT '补全后内容',
    `complete_reason`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '补全原因',
    `operator`         VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '操作者 AI/USER',
    `is_deleted`       TINYINT    NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_knowledge_id` (`knowledge_id`),
    KEY `idx_suggestion_id` (`suggestion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识补全日志表';
