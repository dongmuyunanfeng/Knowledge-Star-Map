package com.knowledgestarmap.service.impl;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.service.AgentToolDispatcherService;
import com.knowledgestarmap.util.RedisKeyBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AgentToolDispatcherServiceImpl implements AgentToolDispatcherService {

    private final Map<String, AgentTool> toolRegistry = new HashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.agent.max-llm-calls-per-minute:3}")
    private int maxLlmCallsPerMinute;

    @Value("${app.agent.max-llm-calls-per-day:100}")
    private int maxLlmCallsPerDay;

    public AgentToolDispatcherServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void registerTool(AgentTool tool) {
        toolRegistry.put(tool.name(), tool);
        log.info("注册Agent工具: {}", tool.name());
    }

    @Override
    public AgentTool getTool(String toolName) {
        return toolRegistry.get(toolName);
    }

    @Override
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> params, Long userId) {
        AgentTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return ToolExecutionResult.failure("未知工具: " + toolName);
        }
        return tool.execute(params, userId);
    }

    @Override
    public List<AgentTool> getToolRegistrySnapshot() {
        return new ArrayList<>(toolRegistry.values());
    }

    @Override
    public boolean checkRateLimit(Long userId) {
        String minuteKey = RedisKeyBuilder.rateLimitPerMinute(userId);
        String dayKey = RedisKeyBuilder.rateLimitPerDay(userId);

        Long minuteCount = redisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount == null || minuteCount == 1) {
            redisTemplate.expire(minuteKey, 60, TimeUnit.SECONDS);
        }

        Long dayCount = redisTemplate.opsForValue().increment(dayKey);
        if (dayCount == null || dayCount == 1) {
            redisTemplate.expire(dayKey, 86400, TimeUnit.SECONDS);
        }

        if (minuteCount != null && minuteCount > maxLlmCallsPerMinute) {
            log.warn("LLM调用频率超限（每分钟）: userId={}, count={}", userId, minuteCount);
            throw new BizException(BizErrorCode.LLM_RATE_LIMIT);
        }
        if (dayCount != null && dayCount > maxLlmCallsPerDay) {
            log.warn("LLM调用频率超限（每天）: userId={}, count={}", userId, dayCount);
            throw new BizException(BizErrorCode.LLM_RATE_LIMIT);
        }

        return true;
    }
}
