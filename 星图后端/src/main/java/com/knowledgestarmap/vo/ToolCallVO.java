package com.knowledgestarmap.vo;

import lombok.Data;

import java.util.Map;

@Data
public class ToolCallVO {
    private String toolName;
    private Map<String, Object> parameters;
    private Boolean success;
    private String errorMessage;
}
