package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectExperienceVO {
    private Long projectId;
    private String projectName;
    private String projectDesc;
    private String projectTechStack;
    private String responsibilities;
    private Integer generateStatus;
    private LocalDateTime updateTime;
}
