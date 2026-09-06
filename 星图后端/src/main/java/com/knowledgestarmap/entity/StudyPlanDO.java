package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("study_plan")
public class StudyPlanDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String planTitle;
    private Integer planType;
    private String planDesc;
    private String targetNeed;
    private String waitKnowledge;
    private Integer priority;
    private Integer finishStatus;
    private BigDecimal progressRate;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
