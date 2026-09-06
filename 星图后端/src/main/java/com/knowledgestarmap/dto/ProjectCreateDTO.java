package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class ProjectCreateDTO {
    private String projectName;
    private String projectDesc;
    private String projectTechStack;
    private String projectRole;
    private String projectHighlights;
    private String sourceFileIds;
}
