CREATE TABLE `search_history` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '检索记录ID',
    `user_id`          BIGINT       NOT NULL COMMENT '用户ID',
    `keyword`          VARCHAR(255) NOT NULL COMMENT '检索关键词',
    `result_count`     INT          NOT NULL DEFAULT 0 COMMENT '本次检索返回结果数量',
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检索历史表';
