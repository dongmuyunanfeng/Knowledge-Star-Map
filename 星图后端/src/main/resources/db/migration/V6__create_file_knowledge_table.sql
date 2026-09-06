CREATE TABLE `file_knowledge` (
    `id`                  BIGINT     NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `user_id`             BIGINT     NOT NULL COMMENT '用户ID',
    `file_id`             BIGINT     NOT NULL COMMENT '源文件ID',
    `knowledge_id`        BIGINT     NOT NULL COMMENT '知识点ID',
    `content_segment`     TEXT                    COMMENT '该知识点在源文件中的原文片段',
    `segment_start`       INT        NOT NULL DEFAULT 0 COMMENT '片段起始位置（字符偏移，左闭）',
    `segment_end`         INT        NOT NULL DEFAULT 0 COMMENT '片段结束位置（字符偏移，右开）',
    `is_deleted`          TINYINT    NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`         DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_knowledge` (`file_id`, `knowledge_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件-知识点关联表';
