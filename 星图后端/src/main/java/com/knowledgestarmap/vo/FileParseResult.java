package com.knowledgestarmap.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileParseResult {
    private boolean parseSuccess;
    private String parsedText;
    private int extractedKnowledgeCount;
    private String errorMessage;
    private List<KnowledgeSliceInfo> knowledgeSlices;
    private int newKnowledgeCount;
    private int duplicateCount;
}
