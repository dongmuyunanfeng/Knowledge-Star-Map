package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_star_map")
public class KnowledgeStarMapDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String domainName;
    private Integer knowledgeCount;
    private BigDecimal masteryScore;
    private Integer weakFlag;
    private BigDecimal xCoordinate;
    private BigDecimal yCoordinate;
    private BigDecimal weightFactor;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
