package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_info")
public class KnowledgeInfoDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String knowledgeName;
    private String knowledgeDomain;
    private String knowledgeTag;
    private Long projectId;
    private String knowledgeContent;
    private String completeContent;
    private Integer masteryLevel;
    private BigDecimal masteryScore;
    private Integer isCompleted;
    private String knowledgeHash;
    private Integer sourceType;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
