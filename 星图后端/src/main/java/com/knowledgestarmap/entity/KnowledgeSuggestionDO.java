package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_suggestion")
public class KnowledgeSuggestionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long knowledgeId;
    private Integer suggestionType;
    private String suggestionTitle;
    private String suggestionContent;
    private String suggestionReason;
    private Integer status;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
