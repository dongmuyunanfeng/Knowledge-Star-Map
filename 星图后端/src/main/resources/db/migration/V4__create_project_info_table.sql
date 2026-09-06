CREATE TABLE `project_info` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    `user_id`               BIGINT      NOT NULL COMMENT '用户ID',
    `project_name`          VARCHAR(128) NOT NULL COMMENT '项目名称',
    `project_desc`          TEXT                     COMMENT '项目整体描述',
    `project_tech_stack`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '项目技术栈，逗号分隔',
    `project_role`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户在项目中的角色/职责',
    `project_highlights`    TEXT                     COMMENT '项目亮点总结',
    `project_source_files`  TEXT                     COMMENT '关联的文件ID列表，逗号分隔',
    `is_deleted`            TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目信息表';
