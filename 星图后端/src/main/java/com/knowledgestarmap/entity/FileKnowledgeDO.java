package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_knowledge")
public class FileKnowledgeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileId;
    private Long knowledgeId;
    private String contentSegment;
    private Integer segmentStart;
    private Integer segmentEnd;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
}
