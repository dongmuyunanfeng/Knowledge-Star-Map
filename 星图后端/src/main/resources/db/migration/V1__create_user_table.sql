CREATE TABLE `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`        VARCHAR(32)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(64)  NOT NULL COMMENT 'BCrypt加密密码',
    `nickname`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像URL',
    `study_direction` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '主攻方向：后端/前端/算法',
    `job_target`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '求职目标',
    `email`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '邮箱',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1正常',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
