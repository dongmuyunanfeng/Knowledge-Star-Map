package com.knowledgestarmap.dto;

import lombok.Data;

@Data
public class KnowledgeQueryParam {
    private String keyword;
    private String domain;
    private String tag;
    private Long projectId;
    private Integer masteryLevel;
    private Integer page = 1;
    private Integer pageSize = 20;
}
