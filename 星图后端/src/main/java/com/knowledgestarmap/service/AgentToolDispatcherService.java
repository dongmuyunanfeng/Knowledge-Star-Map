package com.knowledgestarmap.service;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;

import java.util.List;
import java.util.Map;

public interface AgentToolDispatcherService {
    void registerTool(AgentTool tool);
    AgentTool getTool(String toolName);
    ToolExecutionResult executeTool(String toolName, Map<String, Object> params, Long userId);
    boolean checkRateLimit(Long userId);
    List<AgentTool> getToolRegistrySnapshot();
}
