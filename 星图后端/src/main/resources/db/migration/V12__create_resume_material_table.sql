CREATE TABLE `resume_material` (
    `id`                   BIGINT     NOT NULL AUTO_INCREMENT COMMENT '素材ID',
    `user_id`              BIGINT     NOT NULL COMMENT '用户ID',
    `tech_stack`           TEXT       NOT NULL COMMENT '个人技术栈清单',
    `skill_desc`           TEXT       NOT NULL COMMENT '技能职业化描述',
    `project_highlights`   TEXT       NOT NULL COMMENT '项目亮点总结',
    `resume_summary`       TEXT                COMMENT '简历整体摘要',
    `is_deleted`           TINYINT    NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历素材表';
