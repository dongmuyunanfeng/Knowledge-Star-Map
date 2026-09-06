package com.knowledgestarmap.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KnowledgeStarMapVO {
    private Long id;
    private String domainName;
    private Integer knowledgeCount;
    private BigDecimal masteryScore;
    private Integer weakFlag;
    private BigDecimal xCoordinate;
    private BigDecimal yCoordinate;
    private BigDecimal weightFactor;
}
