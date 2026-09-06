package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeSuggestionVO {
    private Long id;
    private Long knowledgeId;
    private Integer suggestionType;
    private String suggestionTitle;
    private String suggestionContent;
    private String suggestionReason;
    private Integer status;
    private LocalDateTime createTime;
}
