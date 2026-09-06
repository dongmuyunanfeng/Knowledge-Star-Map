package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeTemplateVO {
    private Long id;
    private String templateName;
    private String techStack;
    private String skillDesc;
    private String projectHighlights;
    private String resumeSummary;
    private LocalDateTime createTime;
}
