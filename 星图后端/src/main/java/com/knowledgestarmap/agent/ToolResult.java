package com.knowledgestarmap.agent;

import lombok.Data;

@Data
public class ToolResult {
    private String toolName;
    private Object content;
    private boolean isError;
}
