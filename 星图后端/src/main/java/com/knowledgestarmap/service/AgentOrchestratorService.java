package com.knowledgestarmap.service;

import com.knowledgestarmap.agent.AgentResult;
import com.knowledgestarmap.agent.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

public interface AgentOrchestratorService {
    AgentResult execute(Long userId, String query, List<ChatMessage> history);
    AgentResult execute(Long userId, String query, List<ChatMessage> history, Consumer<String> onAnswerDelta);
    void trimContext(Long userId, int maxRounds);
}
