package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_complete_log")
public class KnowledgeCompleteLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long knowledgeId;
    private Long suggestionId;
    private String oldContent;
    private String newContent;
    private String completeReason;
    private String operator;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
}
