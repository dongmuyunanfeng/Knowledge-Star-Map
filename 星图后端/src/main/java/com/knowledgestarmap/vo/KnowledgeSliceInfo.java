package com.knowledgestarmap.vo;

import lombok.Data;

@Data
public class KnowledgeSliceInfo {
    private String tempId;
    private String name;
    private String tag;
    private String domain;
    private String content;
    private String hash;
    private Long projectId;
    private int startPosition;
    private int endPosition;
    /** 源文件中的位置描述（如"第3行-第20行"），用于溯源展示 */
    private String sourceLocation;
}
