package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_ocr")
public class ImageOcrDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileId;
    private String ocrText;
    private Integer ocrStatus;
    private Integer extractedKnowledgeCount;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
