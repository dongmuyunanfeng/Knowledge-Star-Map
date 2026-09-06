package com.knowledgestarmap.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Redis存储的Agent会话数据结构
 */
@Data
public class AgentSessionDTO {
    private String sessionId;
    private Long userId;
    private String query;
    /** 每轮思考文本列表，thoughtList[i]对应第i轮思考 */
    private List<String> thoughtList;
    /** 每轮工具调用列表，toolCalls[i]对应第i轮的工具调用列表 */
    private List<List<Map<String, Object>>> toolCalls;
    /** 每轮工具观察结果列表，toolObservations[i]对应第i轮的观察列表 */
    private List<List<Map<String, Object>>> toolObservations;
    private Integer roundNo;
    private Integer isPinned;
    private String status;
    private String finalAnswer;
    private Long createTime;
    /** 完整多轮对话历史（每轮用户提问与AI回答交替，按时间顺序），供LLM上下文使用 */
    private List<ChatMessage> historyMessages;
}
