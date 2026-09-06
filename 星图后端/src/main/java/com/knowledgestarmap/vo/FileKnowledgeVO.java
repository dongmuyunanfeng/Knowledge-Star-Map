package com.knowledgestarmap.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FileKnowledgeVO {
    private Long knowledgeId;
    private String knowledgeName;
    private Long fileId;
    private String fileName;
    private String filePath;
    private String contentSegment;
    private Integer segmentStart;
    private Integer segmentEnd;
}
