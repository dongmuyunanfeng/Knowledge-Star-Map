package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class StudyPlanCreateDTO {
    private String planTitle;
    private Integer planType;
    private String planDesc;
    private String targetNeed;
    private Integer priority;
    /** 用户自定义的计划天数，覆盖 planType 的默认 7/30/90；为空时按 planType 默认天数。 */
    private Integer planDays;
}
