package com.knowledgestarmap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudyPlanVO {
    private Long id;
    private String planTitle;
    private Integer planType;
    private String planDesc;
    private String targetNeed;
    private List<WaitKnowledgeVO> waitKnowledge;
    private Integer priority;
    private Integer finishStatus;
    private BigDecimal progressRate;
    private LocalDateTime createTime;
}
