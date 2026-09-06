package com.knowledgestarmap.agent;

import lombok.Data;

@Data
public class ToolExecutionResult {
    private boolean success;
    private Object result;
    private String errorMessage;
    /** 非空表示工具已产出最终回答，编排器应直接使用该文本，不再调用LLM汇总 */
    private String finalAnswer;

    public static ToolExecutionResult success(Object data) {
        ToolExecutionResult r = new ToolExecutionResult();
        r.setSuccess(true);
        r.setResult(data);
        return r;
    }

    public static ToolExecutionResult failure(String message) {
        ToolExecutionResult r = new ToolExecutionResult();
        r.setSuccess(false);
        r.setErrorMessage(message);
        return r;
    }
}
