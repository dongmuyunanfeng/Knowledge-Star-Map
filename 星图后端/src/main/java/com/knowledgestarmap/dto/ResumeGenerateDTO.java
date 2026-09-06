package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class ResumeGenerateDTO {
    private Boolean forceRegenerate = false;
    private String targetNeed;
    private String requirement;
}
