package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class StudyPlanUpdateDTO {
    private String planTitle;
    private String planDesc;
    private Integer priority;
}
