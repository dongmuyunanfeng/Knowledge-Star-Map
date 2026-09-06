package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class KnowledgeQueryDTO {
    private String keyword;
    private String tag;
    private Integer masteryLevel;
    private Integer page = 1;
    private Integer pageSize = 20;
}
