package com.knowledgestarmap.agent;

import lombok.Data;

@Data
public class ToolCallSummary {
    private String toolName;
    private boolean success;
    private int callCount;
    private String errorMessage;
}
