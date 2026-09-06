CREATE TABLE `image_ocr` (
    `id`                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'OCR记录ID',
    `user_id`                     BIGINT       NOT NULL COMMENT '用户ID',
    `file_id`                     BIGINT       NOT NULL COMMENT '关联file_resource.id',
    `ocr_text`                    LONGTEXT     NOT NULL COMMENT 'OCR识别后的纯文本内容',
    `ocr_status`                  TINYINT      NOT NULL DEFAULT 0 COMMENT 'OCR状态 0待识别 1成功 2失败',
    `extracted_knowledge_count`   INT          NOT NULL DEFAULT 0 COMMENT '本次OCR提取的知识点数量',
    `is_deleted`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    `create_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片OCR表';
