CREATE TABLE `project_experience` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`          BIGINT       NOT NULL COMMENT '所属用户',
    `project_id`       BIGINT       NOT NULL COMMENT '关联 project_info.id',
    `role`             VARCHAR(100) DEFAULT NULL COMMENT '项目角色',
    `period`           VARCHAR(50)  DEFAULT NULL COMMENT '项目时间段，如 2024.03 - 2024.06',
    `responsibilities` TEXT         DEFAULT NULL COMMENT '个人职责（STAR，分号分隔）',
    `achievements`     TEXT         DEFAULT NULL COMMENT '项目成果/亮点（分号分隔）',
    `generate_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '生成状态 0待生成 1成功 2失败',
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_project` (`user_id`, `project_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目经历（简历级）';
