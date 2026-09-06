package com.knowledgestarmap.vo;

import lombok.Data;

@Data
public class WaitKnowledgeVO {
    private Long id;
    private String name;
    private String tag;
    private Integer priority;
    private String schedule;
    private String learnContent;
}
