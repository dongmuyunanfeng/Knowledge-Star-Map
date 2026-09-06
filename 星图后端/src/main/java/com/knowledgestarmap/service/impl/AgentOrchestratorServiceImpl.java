package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.knowledgestarmap.agent.AgentResult;
import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ChatMessage;
import com.knowledgestarmap.agent.ToolCallSummary;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.service.AgentOrchestratorService;
import com.knowledgestarmap.service.AgentToolDispatcherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Service
public class AgentOrchestratorServiceImpl implements AgentOrchestratorService {

    private static final int MAX_TOOL_CALLS_PER_ROUND = 5;

    private final AgentToolDispatcherService toolDispatcher;
    private final ChatLlmService chatLlmService;

    @Value("${app.agent.max-rounds:10}")
    private int maxRounds;

    @Value("${app.agent.default-temperature:0.5}")
    private double defaultTemperature;

    @Value("${app.agent.default-max-tokens:1024}")
    private int defaultMaxTokens;

    @Value("${app.agent.max-history-chars:12000}")
    private int maxHistoryChars;

    @Value("${app.agent.max-history-messages:40}")
    private int maxHistoryMessages;

    public AgentOrchestratorServiceImpl(AgentToolDispatcherService toolDispatcher, ChatLlmService chatLlmService) {
        this.toolDispatcher = toolDispatcher;
        this.chatLlmService = chatLlmService;
    }

    @Override
    public AgentResult execute(Long userId, String query, List<ChatMessage> history) {
        return execute(userId, query, history, null);
    }

    /**
     * 执行一轮 Agent 推理。onAnswerDelta 非空时，最终回答会以流式增量回调（用于 SSE 推送）。
     */
    public AgentResult execute(Long userId, String query, List<ChatMessage> history, Consumer<String> onAnswerDelta) {
        List<ChatMessage> messages = trimHistory(history);
        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(query);
        messages.add(userMsg);

        List<String> thoughtList = new ArrayList<>();
        List<List<Map<String, Object>>> toolCallsList = new ArrayList<>();
        List<List<Map<String, Object>>> toolObservationsList = new ArrayList<>();
        List<String> reasoningList = new ArrayList<>();
        Map<String, ToolCallSummary> summaryMap = new HashMap<>();
        // 本会话内已成功执行过的「一次性写入类」工具，跨轮防重复生成（如学习规划/简历）
        Set<String> executedWriteTools = new HashSet<>();
        int roundCount = 0;
        String finalAnswer = null;
        AtomicInteger toolCallCounter = new AtomicInteger(0);
        boolean forcedStop = false;

        while (roundCount < maxRounds) {
            roundCount++;
            List<Map<String, Object>> roundToolCalls = new ArrayList<>();
            List<Map<String, Object>> roundObservations = new ArrayList<>();
            // 每轮独立去重：阻止同轮内同一工具被无限重复调用，但不妨碍跨轮复用
            Set<String> calledToolNames = new HashSet<>();

            String systemPrompt = buildSystemPrompt();
            List<Map<String, Object>> apiMessages = buildApiMessages(messages, toolCallsList, toolObservationsList, reasoningList);

            ToolExecutionResult llmResult;
            try {
                llmResult = callLlm(systemPrompt, apiMessages, userId, onAnswerDelta);
            } catch (BizException e) {
                if (e.getCode() == BizErrorCode.LLM_RATE_LIMIT.getCode()) {
                    throw e;
                }
                llmResult = ToolExecutionResult.failure(e.getMessage());
            }

            if (!llmResult.isSuccess()) {
                thoughtList.add("LLM调用失败: " + llmResult.getErrorMessage());
                toolCallsList.add(roundToolCalls);
                toolObservationsList.add(roundObservations);
                reasoningList.add(null);
                finalAnswer = "很抱歉，AI服务暂时不可用，请稍后重试。错误: " + llmResult.getErrorMessage();
                break;
            }

            JSONObject llmResponse;
            try {
                llmResponse = JSON.parseObject(llmResult.getResult().toString());
            } catch (Exception e) {
                thoughtList.add("LLM返回格式解析失败: " + e.getMessage());
                Map<String, Object> failedObservation = new LinkedHashMap<>();
                failedObservation.put("type", "tool_result");
                failedObservation.put("tool_call_id", "_parse_error_" + roundCount);
                failedObservation.put("content", "LLM响应解析失败: " + e.getMessage());
                roundObservations.add(failedObservation);
                toolCallsList.add(roundToolCalls);
                toolObservationsList.add(roundObservations);
                reasoningList.add(null);
                ChatMessage errorMsg = new ChatMessage();
                errorMsg.setRole("system");
                errorMsg.setContent("AI响应格式解析异常，请尝试重新组织回答。错误: " + e.getMessage());
                messages.add(errorMsg);
                continue;
            }

            String thought = llmResponse.getString("thought");
            if (thought != null) {
                thoughtList.add(thought);
            }
            reasoningList.add(llmResponse.getString("reasoning_content"));

            JSONArray toolCalls = llmResponse.getJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                finalAnswer = llmResponse.getString("answer");
                if (finalAnswer == null) {
                    finalAnswer = "分析完成。";
                }
                toolCallsList.add(roundToolCalls);
                toolObservationsList.add(roundObservations);
                break;
            }

            int callCount = 0;
            String toolFinalAnswer = null;
            for (int i = 0; i < Math.min(toolCalls.size(), MAX_TOOL_CALLS_PER_ROUND); i++) {
                JSONObject tc = toolCalls.getJSONObject(i);
                if (tc == null) {
                    continue;
                }
                String toolName = extractToolName(tc);
                if (toolName == null || toolName.isBlank()) {
                    continue;
                }
                Map<String, Object> params = extractArguments(tc);
                String argumentsStr = JSON.toJSONString(params);

                if (!calledToolNames.add(toolName)) {
                    log.warn("同轮检测到重复工具调用，跳过: tool={}, userId={}", toolName, userId);
                    continue;
                }

                // 跨轮幂等：写入类工具本会话已成功执行过则不再重复执行，避免「重复生成两次」
                if (isWriteTool(toolName) && executedWriteTools.contains(toolName)) {
                    log.warn("本会话已执行过写入工具，跳过重复调用: tool={}, userId={}", toolName, userId);
                    String skipCallId = "call_" + userId + "_" + toolCallCounter.incrementAndGet();
                    Map<String, Object> skipCallEntry = new LinkedHashMap<>();
                    skipCallEntry.put("id", skipCallId);
                    skipCallEntry.put("type", "function");
                    skipCallEntry.put("function", new LinkedHashMap<String, Object>() {{
                        put("name", toolName);
                        put("arguments", argumentsStr);
                    }});
                    roundToolCalls.add(skipCallEntry);
                    Map<String, Object> skipObservation = new LinkedHashMap<>();
                    skipObservation.put("type", "tool_result");
                    skipObservation.put("tool_call_id", skipCallId);
                    skipObservation.put("content", "该工具本次会话已执行并保存结果，请直接引用之前返回的内容完成回答，不要重复生成。");
                    roundObservations.add(skipObservation);
                    continue;
                }

                // 把用户本轮原始提问注入工具参数，供学习规划等工具提取用户明确指定的天数
                params.put("userQuery", query);

                String toolCallId = "call_" + userId + "_" + toolCallCounter.incrementAndGet();

                Map<String, Object> toolCallEntry = new LinkedHashMap<>();
                toolCallEntry.put("id", toolCallId);
                toolCallEntry.put("type", "function");
                toolCallEntry.put("function", new LinkedHashMap<String, Object>() {{
                    put("name", toolName);
                    put("arguments", argumentsStr);
                }});
                roundToolCalls.add(toolCallEntry);

                ToolExecutionResult execution = toolDispatcher.executeTool(toolName, params, userId);

                if (isWriteTool(toolName) && execution.isSuccess()) {
                    executedWriteTools.add(toolName);
                }

                if (execution.isSuccess() && execution.getFinalAnswer() != null && !execution.getFinalAnswer().isBlank()) {
                    toolFinalAnswer = execution.getFinalAnswer();
                }

                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("type", "tool_result");
                observation.put("tool_call_id", toolCallId);
                Object rawResult = execution.getResult();
                observation.put("content", rawResult == null ? ""
                        : (rawResult instanceof CharSequence ? rawResult.toString() : JSON.toJSONString(rawResult)));
                roundObservations.add(observation);

                ToolCallSummary summary = summaryMap.computeIfAbsent(toolName,
                        k -> {
                            ToolCallSummary s = new ToolCallSummary();
                            s.setToolName(k);
                            s.setSuccess(true);
                            s.setCallCount(0);
                            s.setErrorMessage(null);
                            return s;
                        });
                summary.setCallCount(summary.getCallCount() + 1);
                if (!execution.isSuccess()) {
                    summary.setSuccess(false);
                    if (summary.getErrorMessage() == null) {
                        summary.setErrorMessage(execution.getErrorMessage());
                    }
                }

                ChatMessage toolMsg = new ChatMessage();
                toolMsg.setRole("tool");
                toolMsg.setContent(observation.get("content").toString());
                messages.add(toolMsg);
                callCount++;
            }

            toolCallsList.add(roundToolCalls);
            toolObservationsList.add(roundObservations);

            if (toolFinalAnswer != null) {
                finalAnswer = toolFinalAnswer;
                break;
            }

            if (forcedStop) {
                String toolResults = buildFinalAnswerFromToolResults(toolObservationsList);
                String summary = callLlmPlain(
                        "你是知识引导助手。请用自然、清晰的中文，基于用户的知识星图数据向用户总结回答。直接输出回答内容，不要使用JSON格式，不要输出代码块，不要提及工具。",
                        "以下是用户的知识星图相关数据：\n" + toolResults + "\n\n请用自然语言总结这些数据，告诉用户他的知识体系整体情况。",
                        userId, onAnswerDelta);
                finalAnswer = (summary != null && !summary.isBlank()) ? summary : toolResults;
                break;
            }

            if (callCount == 0) {
                finalAnswer = llmResponse.getString("answer");
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "已完成分析。";
        }

        if (roundCount >= maxRounds) {
            log.warn("Agent推理轮次达到上限，返回部分结果: userId={}, roundCount={}", userId, roundCount);
            finalAnswer = finalAnswer + "\n\n（提示：分析已达到最大推理轮次，结果可能不完整。）";
        }

        // 将thoughtList和toolCalls/toolObservations追加到session（通过调用方传入的session对象）
        // Orchestrator不直接修改session，由AgentSessionServiceImpl负责

        AgentResult result = new AgentResult();
        result.setFinalAnswer(finalAnswer);
        result.setRoundCount(roundCount);
        result.setToolCallSummary(new ArrayList<>(summaryMap.values()));
        result.setThought(thoughtList.isEmpty() ? null : thoughtList.get(thoughtList.size() - 1));
        result.setToolCalls(roundCount > 0 ? toolCallsList.get(toolCallsList.size() - 1) : null);
        result.setToolObservations(roundCount > 0 ? toolObservationsList.get(toolObservationsList.size() - 1) : null);

        return result;
    }

    @Override
    public void trimContext(Long userId, int maxRounds) {
        // trimContext is a no-op at the orchestrator level;
        // context trimming is handled by AgentSessionServiceImpl via trimContextInternal
    }

    /**
     * 多轮历史滑动窗口截断：保留首条用户意图，其余按「最近优先」成对丢弃，
     * 同时受字符数与消息条数双重预算约束，避免上下文无限增长撞模型上限。
     */
    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatMessage> result = new ArrayList<>(history);
        int idx = 1; // 索引0为首条用户意图，始终保留
        while (idx < result.size() - 1) {
            if (result.size() <= maxHistoryMessages && totalChars(result) <= maxHistoryChars) {
                break;
            }
            // 成对删除：idx 为 user，其后紧跟 assistant
            result.remove(idx + 1);
            result.remove(idx);
        }
        if (result.size() != history.size()) {
            log.info("多轮历史裁剪: {}条 -> {}条, 字符 {} -> {}", history.size(), result.size(),
                    totalChars(history), totalChars(result));
        }
        return result;
    }

    private int totalChars(List<ChatMessage> msgs) {
        int sum = 0;
        for (ChatMessage m : msgs) {
            if (m.getContent() != null) {
                sum += m.getContent().length();
            }
        }
        return sum;
    }

    private ToolExecutionResult callLlm(String systemPrompt, List<Map<String, Object>> messages, Long userId,
                                        Consumer<String> onAnswerDelta) {
        toolDispatcher.checkRateLimit(userId);

        List<Map<String, Object>> apiMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            apiMessages.add(systemMsg);
        }
        apiMessages.addAll(messages);

        JSONObject message;
        if (onAnswerDelta != null) {
            message = chatLlmService.chatWithToolsStream(apiMessages, buildToolsArray(), defaultTemperature, defaultMaxTokens, onAnswerDelta);
        } else {
            message = chatLlmService.chatWithTools(apiMessages, buildToolsArray(), defaultTemperature, defaultMaxTokens);
        }
        if (message == null) {
            return ToolExecutionResult.failure("LLM服务不可用或超时");
        }
        log.debug("模型2响应: content={}, toolCalls={}", message.getString("content"), message.getJSONArray("tool_calls"));
        return ToolExecutionResult.success(toAgentResult(message));
    }

    private List<Map<String, Object>> buildToolsArray() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (AgentTool tool : toolDispatcher.getToolRegistrySnapshot()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", JSON.parseObject(tool.parametersSchema()));
            Map<String, Object> toolObj = new LinkedHashMap<>();
            toolObj.put("type", "function");
            toolObj.put("function", function);
            tools.add(toolObj);
        }
        return tools;
    }

    private JSONObject toAgentResult(JSONObject message) {
        JSONObject result = new JSONObject();
        String reasoning = message.getString("reasoning_content");
        String content = message.getString("content");
        JSONArray toolCalls = message.getJSONArray("tool_calls");

        String thought = (reasoning != null && !reasoning.isBlank()) ? reasoning : content;
        if (thought != null && !thought.isBlank()) {
            result.put("thought", thought.trim());
        }
        if (reasoning != null && !reasoning.isBlank()) {
            result.put("reasoning_content", reasoning.trim());
        }

        if (toolCalls != null && !toolCalls.isEmpty()) {
            result.put("tool_calls", toolCalls);
        } else if (content != null && !content.isBlank()) {
            result.put("answer", content.trim());
        }
        return result;
    }

    private String callLlmPlain(String systemPrompt, String userMessage, Long userId, Consumer<String> onAnswerDelta) {
        try {
            toolDispatcher.checkRateLimit(userId);
        } catch (BizException e) {
            log.warn("总结调用触发限流: userId={}", userId);
            return null;
        }

        if (onAnswerDelta != null) {
            return chatLlmService.chatStream(systemPrompt, userMessage, defaultTemperature, defaultMaxTokens, onAnswerDelta);
        }
        return chatLlmService.chat(systemPrompt, userMessage, defaultTemperature, defaultMaxTokens);
    }

    /** 一次性写入类工具：本会话只需成功执行一次，避免多轮重复生成落库。 */
    private boolean isWriteTool(String toolName) {
        return "learning_plan_generate".equals(toolName) || "resume_generate".equals(toolName);
    }

    private String buildSystemPrompt() {
        return "你是一个名为「知识星图助手」的 AI Agent，帮助学生管理个人知识体系。你具备推理能力，应先分析用户意图，再决定是否调用工具，最后给出有条理的回答。\n" +
                "\n" +
                "【你能调用的工具】\n" +
                "1. knowledge_query —— 检索用户知识库中的知识点（支持关键词、标签、掌握度筛选），用于回答「我学过什么 / 某个知识点掌握得如何」。\n" +
                "2. star_map_stats —— 查看知识星图统计：知识点总数、平均掌握分、各领域知识点数与薄弱点，用于「分析整体掌握情况 / 找出薄弱领域」。\n" +
                "3. knowledge_suggestion_generate —— 为某个知识点生成补全建议（不直接改库，只生成待确认建议），用于「帮我补全 / 完善某个知识点」。\n" +
                "4. learning_plan_generate —— 生成学习规划（未来待学知识点），用于「帮我制定学习计划」。\n" +
                "5. resume_generate —— 生成简历素材，用于「帮我生成简历」。\n" +
                "\n" +
                "【工作流程】\n" +
                "1. 理解用户意图；\n" +
                "2. 判断需要哪些数据：检索知识用 knowledge_query，分析掌握情况用 star_map_stats，补全用 knowledge_suggestion_generate；\n" +
                "3. 如需多种数据，可在同一轮调用多个不同工具；\n" +
                "4. 综合工具返回的数据，给出最终中文回答。\n" +
                "\n" +
                "【规则】\n" +
                "- 所有分析基于用户的私有知识库，不臆造用户没有的知识；\n" +
                "- 上文已经提供所需数据时，直接基于已有数据回答，不要重复调用相同工具；\n" +
                "- 数据充足后必须立即给出最终回答，不要再调用工具；\n" +
                "- 涉及补全只生成建议，不直接修改知识库；\n" +
                "- 回答使用结构化列表，关键数据用数字量化（如「共 12 个知识点，平均分 65.5，薄弱领域 2 个」）。";
    }

    private List<Map<String, Object>> buildApiMessages(List<ChatMessage> history,
                                                        List<List<Map<String, Object>>> toolCallsHistory,
                                                        List<List<Map<String, Object>>> toolObsHistory,
                                                        List<String> reasoningList) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // 保留完整多轮历史（user/assistant）。tool 结果由下方 toolObsHistory 追加，避免重复
        for (ChatMessage msg : history) {
            if (msg == null) {
                continue;
            }
            String role = msg.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", role);
            m.put("content", msg.getContent());
            messages.add(m);
        }
        // Append assistant + tool_result messages from accumulated tool calls/observations
        for (int i = 0; i < toolCallsHistory.size(); i++) {
            List<Map<String, Object>> roundCalls = toolCallsHistory.get(i);
            List<Map<String, Object>> roundObs = toolObsHistory.get(i);
            if (!roundCalls.isEmpty()) {
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", null);
                assistantMsg.put("tool_calls", roundCalls);
                if (reasoningList != null && i < reasoningList.size()) {
                    String reasoning = reasoningList.get(i);
                    if (reasoning != null && !reasoning.isBlank()) {
                        assistantMsg.put("reasoning_content", reasoning);
                    }
                }
                messages.add(assistantMsg);
            }
            for (Map<String, Object> obs : roundObs) {
                Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                toolResultMsg.put("role", "tool");
                toolResultMsg.put("tool_call_id", obs.get("tool_call_id"));
                toolResultMsg.put("content", obs.get("content"));
                messages.add(toolResultMsg);
            }
        }
        return messages;
    }

    private String extractToolName(JSONObject tc) {
        JSONObject fn = tc.getJSONObject("function");
        if (fn != null) {
            String name = fn.getString("name");
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String name = tc.getString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        // 兼容：部分模型把工具名误填到 type 字段（应为 "function"）
        String type = tc.getString("type");
        if (type != null && !type.isBlank() && !"function".equals(type)) {
            return type;
        }
        return null;
    }

    private Map<String, Object> extractArguments(JSONObject tc) {
        JSONObject fn = tc.getJSONObject("function");
        Object argsVal = fn != null ? fn.get("arguments") : tc.get("arguments");
        return parseArgumentsValue(argsVal);
    }

    private Map<String, Object> parseArgumentsValue(Object argsVal) {
        if (argsVal == null) {
            return new LinkedHashMap<>();
        }
        if (argsVal instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) argsVal);
        }
        if (argsVal instanceof String) {
            String s = ((String) argsVal).trim();
            if (s.isEmpty()) {
                return new LinkedHashMap<>();
            }
            try {
                return JSON.parseObject(s);
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private String buildFinalAnswerFromToolResults(List<List<Map<String, Object>>> toolObservationsList) {
        StringBuilder sb = new StringBuilder();
        for (List<Map<String, Object>> roundObs : toolObservationsList) {
            for (Map<String, Object> obs : roundObs) {
                Object content = obs.get("content");
                if (content == null) {
                    continue;
                }
                String text = content.toString().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(text);
            }
        }
        return sb.length() > 0 ? sb.toString() : "已完成分析。";
    }
}
