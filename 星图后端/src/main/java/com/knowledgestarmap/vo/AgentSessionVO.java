package com.knowledgestarmap.vo;

import com.knowledgestarmap.agent.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AgentSessionVO {
    private String sessionId;
    private Long userId;
    private String query;
    private List<AgentRoundVO> rounds;
    private List<String> thoughtList;
    private List<ToolCallVO> toolCalls;
    private List<ToolObservationVO> toolObservations;
    private Integer roundNo;
    private Integer isPinned;
    private String status;
    private String finalAnswer;
    private LocalDateTime createTime;
    /** 完整多轮对话历史（user/assistant 交替），供前端切回会话时恢复完整内容 */
    private List<ChatMessage> historyMessages;
}
