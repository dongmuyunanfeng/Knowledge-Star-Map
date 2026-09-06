package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchHistoryVO {
    private Long id;
    private String keyword;
    private Integer resultCount;
    private LocalDateTime createTime;
}
