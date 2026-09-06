package com.knowledgestarmap.agent;

import lombok.Data;
import java.util.Map;

@Data
public class ToolCall {
    private String toolName;
    private Map<String, Object> parameters;
}
