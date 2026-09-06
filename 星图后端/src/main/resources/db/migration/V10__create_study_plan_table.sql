CREATE TABLE `study_plan` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    `user_id`          BIGINT        NOT NULL COMMENT '用户ID',
    `plan_title`       VARCHAR(128)  NOT NULL COMMENT '计划标题',
    `plan_type`        TINYINT       NOT NULL COMMENT '计划周期 1短期(7天) 2中期(30天) 3长期(90天)',
    `plan_desc`        TEXT                     COMMENT '学习方案描述',
    `target_need`      VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '用户目标需求',
    `wait_knowledge`   TEXT          NOT NULL COMMENT '待学知识点JSON',
    `priority`         TINYINT       NOT NULL DEFAULT 2 COMMENT '计划优先级 1高 2中 3低',
    `finish_status`    TINYINT       NOT NULL DEFAULT 0 COMMENT '完成状态 0未完成 1已完成',
    `progress_rate`    DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '学习进度百分比 0-100',
    `is_deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_plan_type` (`plan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习规划表';
