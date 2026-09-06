CREATE TABLE `ai_chat_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '对话记录ID',
    `user_id`          BIGINT       NOT NULL COMMENT '用户ID',
    `session_id`       VARCHAR(64)  NOT NULL COMMENT '会话ID（UUID）',
    `user_question`    TEXT         NOT NULL COMMENT '用户问题',
    `ai_answer`        TEXT         NOT NULL COMMENT 'AI回答内容',
    `chat_type`        VARCHAR(32)  NOT NULL DEFAULT '知识咨询' COMMENT '问答类型',
    `is_pinned`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否标记为重要会话 0否 1是',
    `tool_call_log`    LONGTEXT              COMMENT 'Agent多轮会话日志JSON',
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_is_pinned` (`is_pinned`),
    UNIQUE KEY `uk_user_session_deleted` (`user_id`, `session_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI问答记录表';
