package com.knowledgestarmap.vo;

import com.knowledgestarmap.entity.KnowledgeSuggestionDO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeInfoVO {
    private Long id;
    private String knowledgeName;
    private String knowledgeDomain;
    private String knowledgeTag;
    private Long projectId;
    private String projectName;
    private String knowledgeContent;
    private String completeContent;
    private Integer masteryLevel;
    private BigDecimal masteryScore;
    private Integer isCompleted;
    private Integer sourceType;
    private String contentSegment;
    private List<FileKnowledgeVO> fileSources;
    private List<KnowledgeSuggestionVO> suggestions;
}
