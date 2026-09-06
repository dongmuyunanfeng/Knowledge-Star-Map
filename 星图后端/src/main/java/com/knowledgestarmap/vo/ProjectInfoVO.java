package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectInfoVO {
    private Long id;
    private String projectName;
    private String projectDesc;
    private String projectTechStack;
    private String projectRole;
    private String projectHighlights;
    private String projectSourceFiles;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
