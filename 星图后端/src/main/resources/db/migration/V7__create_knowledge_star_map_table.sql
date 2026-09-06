CREATE TABLE `knowledge_star_map` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '星图记录ID',
    `user_id`         BIGINT        NOT NULL COMMENT '用户ID',
    `domain_name`     VARCHAR(64)   NOT NULL COMMENT '知识领域名称',
    `knowledge_count` INT           NOT NULL DEFAULT 0 COMMENT '该领域知识点总数',
    `mastery_score`   DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '领域掌握度平均分',
    `weak_flag`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否薄弱领域 0否 1是',
    `x_coordinate`    DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '星图X布局坐标',
    `y_coordinate`    DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '星图Y布局坐标',
    `weight_factor`   DECIMAL(5,2)  NOT NULL DEFAULT 1.00 COMMENT '星点权重系数',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识星图表';
