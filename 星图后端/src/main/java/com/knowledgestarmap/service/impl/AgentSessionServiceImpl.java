package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.knowledgestarmap.agent.AgentResult;
import com.knowledgestarmap.agent.AgentSessionDTO;
import com.knowledgestarmap.agent.ChatMessage;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.entity.AiChatLogDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.AiChatLogMapper;
import com.knowledgestarmap.service.AgentOrchestratorService;
import com.knowledgestarmap.service.AgentSessionService;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.AgentRoundVO;
import com.knowledgestarmap.vo.AgentSessionVO;
import com.knowledgestarmap.vo.ToolCallVO;
import com.knowledgestarmap.vo.ToolObservationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentSessionServiceImpl implements AgentSessionService {

    private static final int REDIS_TTL_HOURS = 24;
    private static final int MAX_TOOL_CALL_LOG_CHARS = 500000;
    private static final String SESSION_LIST_KEY_PREFIX = "agent:session:list:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AiChatLogMapper aiChatLogMapper;
    private final AgentOrchestratorService orchestratorService;

    @Value("${app.agent.max-rounds:10}")
    private int maxRounds;

    public AgentSessionServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                   AiChatLogMapper aiChatLogMapper,
                                   AgentOrchestratorService orchestratorService) {
        this.redisTemplate = redisTemplate;
        this.aiChatLogMapper = aiChatLogMapper;
        this.orchestratorService = orchestratorService;
    }

    @Override
    public AgentResult createSession(Long userId, String query) {
        return createSession(userId, query, null);
    }

    @Override
    public AgentResult createSession(Long userId, String query, Consumer<String> onAnswerDelta) {
        return createSession(userId, query, onAnswerDelta, UUID.randomUUID().toString());
    }

    @Override
    public AgentResult createSession(Long userId, String query, Consumer<String> onAnswerDelta, String sessionId) {
        AgentSessionDTO session = new AgentSessionDTO();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setQuery(query);
        session.setThoughtList(new ArrayList<>());
        session.setToolCalls(new ArrayList<>());
        session.setToolObservations(new ArrayList<>());
        session.setRoundNo(0);
        session.setIsPinned(0);
        session.setStatus("active");
        session.setFinalAnswer(null);
        session.setCreateTime(System.currentTimeMillis());

        saveSessionToRedis(session);
        addToSessionList(sessionId, userId);
        AgentResult result = runAgentRound(session, query, onAnswerDelta);
        result.setSessionId(sessionId);
        return result;
    }

    @Override
    public AgentResult continueSession(String sessionId, Long userId, String query) {
        return continueSession(sessionId, userId, query, null);
    }

    @Override
    public AgentResult continueSession(String sessionId, Long userId, String query, Consumer<String> onAnswerDelta) {
        AgentSessionDTO session = loadSession(sessionId, userId);
        if (session == null) {
            throw new BizException(BizErrorCode.SESSION_EXPIRED);
        }
        AgentResult result = runAgentRound(session, query, onAnswerDelta);
        result.setSessionId(sessionId);

        // 检查tool_call_log长度，超限则裁剪
        String sessionJson = (String) redisTemplate.opsForValue().get(RedisKeyBuilder.agentSession(sessionId));
        if (sessionJson != null && sessionJson.length() > MAX_TOOL_CALL_LOG_CHARS) {
            trimContextInternal(sessionId, userId, maxRounds);
        }

        return result;
    }

    @Override
    public void terminateSession(String sessionId, Long userId) {
        String key = RedisKeyBuilder.agentSession(sessionId);
        String sessionJson = (String) redisTemplate.opsForValue().get(key);
        if (sessionJson != null) {
            AgentSessionDTO session = JSON.parseObject(sessionJson, AgentSessionDTO.class);
            if (!userId.equals(session.getUserId())) {
                throw new BizException(BizErrorCode.FORBIDDEN);
            }
            removeFromSessionList(sessionId, userId);
        }
        redisTemplate.delete(key);

        // 已收藏落库的会话也要一并软删除，否则会在列表里"复活"
        aiChatLogMapper.update(null, new UpdateWrapper<AiChatLogDO>()
                .set("is_deleted", 1)
                .eq("session_id", sessionId)
                .eq("user_id", userId)
                .eq("is_deleted", 0));
    }

    @Override
    public void pinSession(String sessionId, Long userId) {
        String key = RedisKeyBuilder.agentSession(sessionId);
        String sessionJson = (String) redisTemplate.opsForValue().get(key);

        if (sessionJson == null) {
            AiChatLogDO existing = aiChatLogMapper.selectOne(
                    new LambdaQueryWrapper<AiChatLogDO>()
                            .eq(AiChatLogDO::getSessionId, sessionId)
                            .eq(AiChatLogDO::getIsDeleted, 0));
            if (existing != null && existing.getIsPinned() == 1) {
                throw new BizException(BizErrorCode.SESSION_ALREADY_PINNED);
            }
            throw new BizException(BizErrorCode.SESSION_EXPIRED);
        }

        // 深拷贝保存原始完整数据，避免trimContext截断后持久化不完整历史
        AgentSessionDTO session = JSON.parseObject(sessionJson, AgentSessionDTO.class);
        if (!userId.equals(session.getUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN);
        }

        // 查重：同一会话已收藏则抛错，避免重复插入多行
        Long pinnedCount = aiChatLogMapper.selectCount(
                new LambdaQueryWrapper<AiChatLogDO>()
                        .eq(AiChatLogDO::getSessionId, sessionId)
                        .eq(AiChatLogDO::getUserId, userId)
                        .eq(AiChatLogDO::getIsPinned, 1)
                        .eq(AiChatLogDO::getIsDeleted, 0));
        if (pinnedCount != null && pinnedCount > 0) {
            throw new BizException(BizErrorCode.SESSION_ALREADY_PINNED);
        }

        AiChatLogDO logDO = buildAiChatLogDO(session);
        aiChatLogMapper.insert(logDO);

        log.info("会话已收藏持久化: sessionId={}, userId={}", sessionId, userId);
    }

    @Override
    public void unpinSession(String sessionId, Long userId) {
        int affected = aiChatLogMapper.update(null, new UpdateWrapper<AiChatLogDO>()
                .set("is_deleted", 1)
                .eq("session_id", sessionId)
                .eq("user_id", userId)
                .eq("is_pinned", 1)
                .eq("is_deleted", 0));
        if (affected == 0) {
            throw new BizException(BizErrorCode.SESSION_NOT_FOUND);
        }
        log.info("会话已取消收藏: sessionId={}, userId={}", sessionId, userId);
    }

    @Override
    public AgentSessionVO getSession(String sessionId, Long userId) {
        // 优先查Redis
        AgentSessionDTO session = loadSession(sessionId, userId);
        if (session != null) {
            return convertToVO(session, true);
        }

        // 查DB（已pin的会话）
        AiChatLogDO logDO = aiChatLogMapper.selectOne(
                new LambdaQueryWrapper<AiChatLogDO>()
                        .eq(AiChatLogDO::getSessionId, sessionId)
                        .eq(AiChatLogDO::getUserId, userId)
                        .eq(AiChatLogDO::getIsDeleted, 0));

        if (logDO == null) {
            throw new BizException(BizErrorCode.SESSION_NOT_FOUND);
        }
        return convertLogToVO(logDO);
    }

    @Override
    public PageResult<AgentSessionVO> listSessions(Long userId, int page, int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);

        List<AgentSessionVO> allSessions = new ArrayList<>();

        // 从Redis查活跃会话
        List<String> sessionIds = getFromSessionList(userId);
        for (String sid : sessionIds) {
            String sessionJson = (String) redisTemplate.opsForValue().get(RedisKeyBuilder.agentSession(sid));
            if (sessionJson != null) {
                try {
                    AgentSessionDTO s = JSON.parseObject(sessionJson, AgentSessionDTO.class);
                    if (userId.equals(s.getUserId())) {
                        allSessions.add(convertToVO(s, false));
                    }
                } catch (Exception e) {
                    log.warn("解析Redis会话失败: sessionId={}", sid, e);
                }
            }
        }

        // 从DB查已pin会话
        long dbTotal = aiChatLogMapper.selectCount(
                new LambdaQueryWrapper<AiChatLogDO>()
                        .eq(AiChatLogDO::getUserId, userId)
                        .eq(AiChatLogDO::getIsPinned, 1)
                        .eq(AiChatLogDO::getIsDeleted, 0));

        List<AiChatLogDO> dbLogs = aiChatLogMapper.selectList(
                new LambdaQueryWrapper<AiChatLogDO>()
                        .eq(AiChatLogDO::getUserId, userId)
                        .eq(AiChatLogDO::getIsPinned, 1)
                        .eq(AiChatLogDO::getIsDeleted, 0)
                        .orderByDesc(AiChatLogDO::getCreateTime)
                        .last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize)));

        for (AiChatLogDO logDO : dbLogs) {
            allSessions.add(convertLogToVO(logDO));
        }

        // 合并后按createTime倒序排序
        allSessions.sort((a, b) -> {
            LocalDateTime dtA = a.getCreateTime() != null ? a.getCreateTime() : LocalDateTime.MIN;
            LocalDateTime dtB = b.getCreateTime() != null ? b.getCreateTime() : LocalDateTime.MIN;
            return dtB.compareTo(dtA);
        });

        // 分页
        int total = allSessions.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<AgentSessionVO> paged = fromIndex < total ? allSessions.subList(fromIndex, toIndex) : new ArrayList<>();

        return PageResult.of(total, page, pageSize, paged);
    }

    @Override
    public void trimContext(String sessionId, Long userId, int maxRounds) {
        trimContextInternal(sessionId, userId, maxRounds);
    }

    private AgentResult runAgentRound(AgentSessionDTO session, String query) {
        return runAgentRound(session, query, null);
    }

    private AgentResult runAgentRound(AgentSessionDTO session, String query, Consumer<String> onAnswerDelta) {
        // 标记"生成中"并先把本轮用户提问落库，前端切换会话后据此识别该会话仍在推理并轮询等待
        session.setStatus("active");
        appendUserHistory(session, query);
        saveSessionToRedis(session);

        List<ChatMessage> history = buildHistoryFromSession(session);
        // 去掉刚写入的本轮提问，避免 execute 内部再次追加导致上下文重复
        if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).getRole())) {
            history.remove(history.size() - 1);
        }
        AgentResult result = orchestratorService.execute(session.getUserId(), query, history, onAnswerDelta);

        // 持久化本轮AI回答，保证多轮上下文
        appendAssistantHistory(session, result.getFinalAnswer());

        // 更新session
        int currentRounds = session.getRoundNo() != null ? session.getRoundNo() : 0;
        session.setRoundNo(currentRounds + 1);
        session.setFinalAnswer(result.getFinalAnswer());
        session.setStatus("completed");

        // 追加本轮思考链数据
        if (result.getThought() != null) {
            session.getThoughtList().add(result.getThought());
        }
        if (result.getToolCalls() != null) {
            session.getToolCalls().add(result.getToolCalls());
        } else {
            session.getToolCalls().add(new ArrayList<>());
        }
        if (result.getToolObservations() != null) {
            session.getToolObservations().add(result.getToolObservations());
        } else {
            session.getToolObservations().add(new ArrayList<>());
        }

        saveSessionToRedis(session);
        return result;
    }

    private List<ChatMessage> buildHistoryFromSession(AgentSessionDTO session) {
        if (session.getHistoryMessages() != null) {
            return new ArrayList<>(session.getHistoryMessages());
        }
        return new ArrayList<>();
    }

    private void appendUserHistory(AgentSessionDTO session, String query) {
        if (session.getHistoryMessages() == null) {
            session.setHistoryMessages(new ArrayList<>());
        }
        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(query);
        session.getHistoryMessages().add(userMsg);
    }

    private void appendAssistantHistory(AgentSessionDTO session, String finalAnswer) {
        if (session.getHistoryMessages() == null) {
            session.setHistoryMessages(new ArrayList<>());
        }
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(finalAnswer != null ? finalAnswer : "");
        session.getHistoryMessages().add(assistantMsg);
    }

    private AgentSessionDTO loadSession(String sessionId, Long userId) {
        String key = RedisKeyBuilder.agentSession(sessionId);
        String sessionJson = (String) redisTemplate.opsForValue().get(key);
        if (sessionJson == null) {
            return null;
        }
        AgentSessionDTO session = JSON.parseObject(sessionJson, AgentSessionDTO.class);
        if (!userId.equals(session.getUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void saveSessionToRedis(AgentSessionDTO session) {
        String key = RedisKeyBuilder.agentSession(session.getSessionId());
        redisTemplate.opsForValue().set(key, JSON.toJSONString(session), REDIS_TTL_HOURS, TimeUnit.HOURS);
    }

    private void addToSessionList(String sessionId, Long userId) {
        String listKey = SESSION_LIST_KEY_PREFIX + userId;
        redisTemplate.opsForList().leftPush(listKey, sessionId);
        redisTemplate.expire(listKey, REDIS_TTL_HOURS + 1, TimeUnit.HOURS);
    }

    private void removeFromSessionList(String sessionId, Long userId) {
        String listKey = SESSION_LIST_KEY_PREFIX + userId;
        redisTemplate.opsForList().remove(listKey, 1, sessionId);
    }

    private List<String> getFromSessionList(Long userId) {
        String listKey = SESSION_LIST_KEY_PREFIX + userId;
        // 限制加载数量，防止会话过多导致OOM
        List<Object> raw = redisTemplate.opsForList().range(listKey, 0, 199L);
        if (raw == null) {
            return new ArrayList<>();
        }
        return raw.stream()
                .map(o -> (String) o)
                .collect(Collectors.toList());
    }

    private AiChatLogDO buildAiChatLogDO(AgentSessionDTO session) {
        AiChatLogDO logDO = new AiChatLogDO();
        logDO.setUserId(session.getUserId());
        logDO.setSessionId(session.getSessionId());
        logDO.setUserQuestion(session.getQuery());
        logDO.setAiAnswer(session.getFinalAnswer());
        logDO.setChatType(inferChatType(session.getQuery()));
        logDO.setIsPinned(1);
        logDO.setIsDeleted(0);

        Map<String, Object> toolCallLog = new LinkedHashMap<>();
        toolCallLog.put("roundCount", session.getRoundNo() != null ? session.getRoundNo() : 0);
        toolCallLog.put("thoughtList", session.getThoughtList() != null ? session.getThoughtList() : new ArrayList<>());

        List<Map<String, Object>> rounds = new ArrayList<>();
        int roundsCount = session.getRoundNo() != null ? session.getRoundNo() : 0;
        List<List<Map<String, Object>>> toolCallsList = session.getToolCalls() != null ? session.getToolCalls() : new ArrayList<>();
        List<List<Map<String, Object>>> toolObsList = session.getToolObservations() != null ? session.getToolObservations() : new ArrayList<>();

        for (int i = 0; i < roundsCount; i++) {
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("roundNo", i + 1);

            String thought = i < (session.getThoughtList() != null ? session.getThoughtList().size() : 0)
                    ? session.getThoughtList().get(i) : null;
            round.put("agentThought", thought);

            List<ToolCallVO> roundToolCalls = new ArrayList<>();
            List<Map<String, Object>> roundCalls = i < toolCallsList.size() ? toolCallsList.get(i) : new ArrayList<>();
            for (Map<String, Object> tc : roundCalls) {
                ToolCallVO vo = new ToolCallVO();
                vo.setToolName((String) tc.get("toolName"));
                vo.setParameters((Map<String, Object>) tc.get("parameters"));
                vo.setSuccess(true);
                vo.setErrorMessage(null);
                roundToolCalls.add(vo);
            }
            round.put("toolCalls", roundToolCalls);

            List<ToolObservationVO> roundObs = new ArrayList<>();
            List<Map<String, Object>> roundObsList = i < toolObsList.size() ? toolObsList.get(i) : new ArrayList<>();
            for (Map<String, Object> obs : roundObsList) {
                ToolObservationVO vo = new ToolObservationVO();
                vo.setToolName((String) obs.get("toolName"));
                vo.setResult(obs.get("result"));
                vo.setSuccess(true);
                roundObs.add(vo);
            }
            round.put("toolObservations", roundObs);

            rounds.add(round);
        }
        toolCallLog.put("rounds", rounds);

        String toolCallLogJson = JSON.toJSONString(toolCallLog);
        if (toolCallLogJson.length() > MAX_TOOL_CALL_LOG_CHARS) {
            toolCallLogJson = toolCallLogJson.substring(0, MAX_TOOL_CALL_LOG_CHARS);
        }
        logDO.setToolCallLog(toolCallLogJson);
        return logDO;
    }

    private AgentSessionVO convertToVO(AgentSessionDTO session, boolean includeRounds) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setUserId(session.getUserId());
        vo.setQuery(session.getQuery());
        vo.setRoundNo(session.getRoundNo());
        vo.setIsPinned(session.getIsPinned());
        vo.setStatus(session.getStatus());
        vo.setFinalAnswer(session.getFinalAnswer());
        vo.setThoughtList(session.getThoughtList());
        vo.setHistoryMessages(session.getHistoryMessages());
        vo.setCreateTime(session.getCreateTime() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(session.getCreateTime()), ZoneId.of("Asia/Shanghai"))
                : null);

        if (includeRounds) {
            vo.setRounds(buildRounds(session));
            vo.setToolCalls(buildFlatToolCalls(session));
            vo.setToolObservations(buildFlatToolObservations(session));
        }

        return vo;
    }

    private AgentSessionVO convertLogToVO(AiChatLogDO logDO) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.setSessionId(logDO.getSessionId());
        vo.setUserId(logDO.getUserId());
        vo.setQuery(logDO.getUserQuestion());
        vo.setIsPinned(logDO.getIsPinned());
        vo.setStatus("completed");
        vo.setFinalAnswer(logDO.getAiAnswer());
        vo.setCreateTime(logDO.getCreateTime());

        if (logDO.getToolCallLog() != null) {
            try {
                JSONObject logJson = JSON.parseObject(logDO.getToolCallLog());
                vo.setRoundNo(logJson.getInteger("roundCount"));
                vo.setThoughtList(logJson.getObject("thoughtList", List.class));

                JSONArray rounds = logJson.getJSONArray("rounds");
                if (rounds != null) {
                    List<AgentRoundVO> roundVOs = new ArrayList<>();
                    for (int i = 0; i < rounds.size(); i++) {
                        JSONObject round = rounds.getJSONObject(i);
                        AgentRoundVO roundVO = new AgentRoundVO();
                        roundVO.setRoundNo(round.getInteger("roundNo"));
                        roundVO.setAgentThought(round.getString("agentThought"));
                        roundVO.setToolCalls(round.getObject("toolCalls", List.class));
                        roundVO.setToolObservations(round.getObject("toolObservations", List.class));
                        roundVOs.add(roundVO);
                    }
                    vo.setRounds(roundVOs);
                }
            } catch (Exception e) {
                log.warn("解析tool_call_log失败: sessionId={}", logDO.getSessionId(), e);
            }
        }

        return vo;
    }

    private List<AgentRoundVO> buildRounds(AgentSessionDTO session) {
        List<AgentRoundVO> rounds = new ArrayList<>();
        int roundsCount = session.getRoundNo() != null ? session.getRoundNo() : 0;
        List<String> thoughtList = session.getThoughtList() != null ? session.getThoughtList() : new ArrayList<>();
        List<List<Map<String, Object>>> toolCallsList = session.getToolCalls() != null ? session.getToolCalls() : new ArrayList<>();
        List<List<Map<String, Object>>> toolObsList = session.getToolObservations() != null ? session.getToolObservations() : new ArrayList<>();

        for (int i = 0; i < roundsCount && i < thoughtList.size(); i++) {
            AgentRoundVO roundVO = new AgentRoundVO();
            roundVO.setRoundNo(i + 1);
            roundVO.setAgentThought(thoughtList.get(i));

            List<ToolCallVO> toolCallVOs = new ArrayList<>();
            List<Map<String, Object>> roundCalls = i < toolCallsList.size() ? toolCallsList.get(i) : new ArrayList<>();
            for (Map<String, Object> tc : roundCalls) {
                ToolCallVO vo = new ToolCallVO();
                vo.setToolName((String) tc.get("toolName"));
                vo.setParameters((Map<String, Object>) tc.get("parameters"));
                vo.setSuccess(true);
                vo.setErrorMessage(null);
                toolCallVOs.add(vo);
            }
            roundVO.setToolCalls(toolCallVOs);

            List<ToolObservationVO> obsVOs = new ArrayList<>();
            List<Map<String, Object>> roundObs = i < toolObsList.size() ? toolObsList.get(i) : new ArrayList<>();
            for (Map<String, Object> obs : roundObs) {
                ToolObservationVO vo = new ToolObservationVO();
                vo.setToolName((String) obs.get("toolName"));
                vo.setResult(obs.get("result"));
                vo.setSuccess(true);
                obsVOs.add(vo);
            }
            roundVO.setToolObservations(obsVOs);

            rounds.add(roundVO);
        }
        return rounds;
    }

    private List<ToolCallVO> buildFlatToolCalls(AgentSessionDTO session) {
        List<ToolCallVO> all = new ArrayList<>();
        if (session.getToolCalls() == null) return all;
        for (List<Map<String, Object>> roundCalls : session.getToolCalls()) {
            for (Map<String, Object> tc : roundCalls) {
                ToolCallVO vo = new ToolCallVO();
                vo.setToolName((String) tc.get("toolName"));
                vo.setParameters((Map<String, Object>) tc.get("parameters"));
                vo.setSuccess(true);
                vo.setErrorMessage(null);
                all.add(vo);
            }
        }
        return all;
    }

    private List<ToolObservationVO> buildFlatToolObservations(AgentSessionDTO session) {
        List<ToolObservationVO> all = new ArrayList<>();
        if (session.getToolObservations() == null) return all;
        for (List<Map<String, Object>> roundObs : session.getToolObservations()) {
            for (Map<String, Object> obs : roundObs) {
                ToolObservationVO vo = new ToolObservationVO();
                vo.setToolName((String) obs.get("toolName"));
                vo.setResult(obs.get("result"));
                vo.setSuccess(true);
                all.add(vo);
            }
        }
        return all;
    }

    private void trimContextInternal(String sessionId, Long userId, int maxRounds) {
        String key = RedisKeyBuilder.agentSession(sessionId);
        String sessionJson = (String) redisTemplate.opsForValue().get(key);
        if (sessionJson == null) {
            return;
        }

        AgentSessionDTO original = JSON.parseObject(sessionJson, AgentSessionDTO.class);
        if (!userId.equals(original.getUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN);
        }

        // 深拷贝
        AgentSessionDTO copy = JSON.parseObject(JSON.toJSONString(original), AgentSessionDTO.class);

        int currentRounds = copy.getRoundNo() != null ? copy.getRoundNo() : 0;
        if (currentRounds <= maxRounds) {
            return;
        }

        int roundsToRemove = currentRounds - maxRounds;

        List<String> thoughtList = copy.getThoughtList();
        if (thoughtList != null && thoughtList.size() > roundsToRemove) {
            copy.setThoughtList(thoughtList.subList(roundsToRemove, thoughtList.size()));
        }

        List<List<Map<String, Object>>> toolCalls = copy.getToolCalls();
        if (toolCalls != null && toolCalls.size() > roundsToRemove) {
            copy.setToolCalls(toolCalls.subList(roundsToRemove, toolCalls.size()));
        }

        List<List<Map<String, Object>>> toolObservations = copy.getToolObservations();
        if (toolObservations != null && toolObservations.size() > roundsToRemove) {
            copy.setToolObservations(toolObservations.subList(roundsToRemove, toolObservations.size()));
        }

        copy.setRoundNo(currentRounds - roundsToRemove);

        redisTemplate.opsForValue().set(key, JSON.toJSONString(copy), REDIS_TTL_HOURS, TimeUnit.HOURS);
        log.info("trimContext完成: sessionId={}, 裁剪{}轮, 保留{}轮", sessionId, roundsToRemove, copy.getRoundNo());
    }

    private String inferChatType(String query) {
        if (query == null) return "知识咨询";
        String lower = query.toLowerCase();
        if (lower.contains("计划") || lower.contains("规划") || lower.contains("学习")) {
            return "学习规划";
        }
        if (lower.contains("简历") || lower.contains("求职") || lower.contains("面试")) {
            return "简历建议";
        }
        return "知识咨询";
    }
}
