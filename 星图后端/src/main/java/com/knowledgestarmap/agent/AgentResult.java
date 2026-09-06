package com.knowledgestarmap.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentResult {
    private String sessionId;
    private String finalAnswer;
    private int roundCount;
    private List<ToolCallSummary> toolCallSummary;
    /** 本轮思考文本（追加到session的thoughtList） */
    private String thought;
    /** 本轮工具调用列表 */
    private List<Map<String, Object>> toolCalls;
    /** 本轮工具观察结果列表 */
    private List<Map<String, Object>> toolObservations;
}
