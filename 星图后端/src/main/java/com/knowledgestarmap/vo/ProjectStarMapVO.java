package com.knowledgestarmap.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProjectStarMapVO {
    private Long id;
    private String projectName;
    private Integer knowledgeCount;
    private BigDecimal masteryScore;
    private Integer weakFlag;
    private BigDecimal xCoordinate;
    private BigDecimal yCoordinate;
}
