package com.knowledgestarmap.agent;

import lombok.Data;

@Data
public class ToolCallOutcome {
    private boolean success;
    private Object data;
    private String errorMessage;
}
