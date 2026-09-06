package com.knowledgestarmap.service;

import com.knowledgestarmap.agent.AgentResult;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.vo.AgentSessionVO;

import java.util.function.Consumer;

public interface AgentSessionService {
    AgentResult createSession(Long userId, String query);
    AgentResult createSession(Long userId, String query, Consumer<String> onAnswerDelta);
    AgentResult createSession(Long userId, String query, Consumer<String> onAnswerDelta, String sessionId);
    AgentResult continueSession(String sessionId, Long userId, String query);
    AgentResult continueSession(String sessionId, Long userId, String query, Consumer<String> onAnswerDelta);
    void terminateSession(String sessionId, Long userId);
    void pinSession(String sessionId, Long userId);
    void unpinSession(String sessionId, Long userId);
    AgentSessionVO getSession(String sessionId, Long userId);
    PageResult<AgentSessionVO> listSessions(Long userId, int page, int pageSize);
    void trimContext(String sessionId, Long userId, int maxRounds);
}
