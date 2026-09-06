package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.knowledgestarmap.entity.KnowledgeCompleteLogDO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.KnowledgeSuggestionDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.KnowledgeCompleteLogMapper;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.KnowledgeSuggestionMapper;
import com.knowledgestarmap.service.KnowledgeCompletionService;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.service.UserConfigService;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeCompletionServiceImpl implements KnowledgeCompletionService {

    private static final int MAX_SUGGESTION_CONTENT_LEN = 20000;
    private static final int MAX_SUGGESTION_REASON_LEN = 20000;
    private static final List<Integer> VALID_SUGGESTION_TYPES = List.of(1, 2, 3);

    private final KnowledgeSuggestionMapper knowledgeSuggestionMapper;
    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final KnowledgeCompleteLogMapper knowledgeCompleteLogMapper;
    private final KnowledgeStarMapService knowledgeStarMapService;
    private final UserConfigService userConfigService;
    private final ChatLlmService chatLlmService;
    private final StringRedisTemplate stringRedisTemplate;

    public KnowledgeCompletionServiceImpl(KnowledgeSuggestionMapper knowledgeSuggestionMapper,
                                          KnowledgeInfoMapper knowledgeInfoMapper,
                                          KnowledgeCompleteLogMapper knowledgeCompleteLogMapper,
                                          KnowledgeStarMapService knowledgeStarMapService,
                                          UserConfigService userConfigService,
                                          ChatLlmService chatLlmService,
                                          StringRedisTemplate stringRedisTemplate) {
        this.knowledgeSuggestionMapper = knowledgeSuggestionMapper;
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.knowledgeCompleteLogMapper = knowledgeCompleteLogMapper;
        this.knowledgeStarMapService = knowledgeStarMapService;
        this.userConfigService = userConfigService;
        this.chatLlmService = chatLlmService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public KnowledgeSuggestionVO generateSuggestion(Long knowledgeId, Integer suggestionType, Long userId) {
        KnowledgeInfoDO knowledge = knowledgeInfoMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getId, knowledgeId)
                        .eq(KnowledgeInfoDO::getUserId, userId)
        );
        if (knowledge == null) {
            throw new BizException(BizErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        if (suggestionType == null || !VALID_SUGGESTION_TYPES.contains(suggestionType)) {
            throw new BizException(BizErrorCode.PARAM_ERROR);
        }

        KnowledgeSuggestionDO existingPending = knowledgeSuggestionMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeSuggestionDO>()
                        .eq(KnowledgeSuggestionDO::getUserId, userId)
                        .eq(KnowledgeSuggestionDO::getKnowledgeId, knowledgeId)
                        .eq(KnowledgeSuggestionDO::getStatus, 0)
        );

        if (existingPending != null) {
            return toVO(existingPending);
        }

        SuggestionGen gen = tryGenerateByLlm(suggestionType, knowledge);
        if (gen.content == null || gen.content.isBlank()) {
            throw new BizException(BizErrorCode.LLM_RESPONSE_INVALID, "补全建议生成失败，请稍后重试");
        }
        String content = gen.content;
        if (content.length() > MAX_SUGGESTION_CONTENT_LEN) {
            content = content.substring(0, MAX_SUGGESTION_CONTENT_LEN);
        }
        String reason = gen.reason != null && !gen.reason.isBlank() ? gen.reason : generateReason(suggestionType, knowledge);
        if (reason.length() > MAX_SUGGESTION_REASON_LEN) {
            reason = reason.substring(0, MAX_SUGGESTION_REASON_LEN);
        }

        KnowledgeSuggestionDO suggestion = new KnowledgeSuggestionDO();
        suggestion.setUserId(userId);
        suggestion.setKnowledgeId(knowledgeId);
        suggestion.setSuggestionType(suggestionType);
        suggestion.setSuggestionTitle(generateTitle(suggestionType, knowledge));
        suggestion.setSuggestionContent(content);
        suggestion.setSuggestionReason(reason);
        suggestion.setStatus(0);
        suggestion.setIsDeleted(0);
        suggestion.setCreateTime(LocalDateTime.now());
        suggestion.setUpdateTime(LocalDateTime.now());

        try {
            knowledgeSuggestionMapper.insert(suggestion);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(BizErrorCode.SUGGESTION_ALREADY_EXISTS);
        }

        return toVO(suggestion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveSuggestion(Long suggestionId, Long userId) {
        KnowledgeSuggestionDO suggestion = knowledgeSuggestionMapper.selectById(suggestionId);
        if (suggestion == null || !userId.equals(suggestion.getUserId())) {
            throw new BizException(BizErrorCode.SUGGESTION_NOT_FOUND);
        }
        Long knowledgeId = suggestion.getKnowledgeId();

        // ① 同知识点其余pending→rejected
        knowledgeSuggestionMapper.update(null, new UpdateWrapper<KnowledgeSuggestionDO>()
                .set("status", 2)
                .eq("user_id", userId)
                .eq("knowledge_id", knowledgeId)
                .eq("status", 0)
                .ne("id", suggestionId)
        );

        // ② 本条→已采纳，影响行数=0则抛1302
        int affected = knowledgeSuggestionMapper.update(null, new UpdateWrapper<KnowledgeSuggestionDO>()
                .set("status", 1)
                .eq("id", suggestionId)
                .eq("user_id", userId)
                .eq("status", 0)
        );
        if (affected == 0) {
            throw new BizException(BizErrorCode.SUGGESTION_ALREADY_PROCESSED);
        }

        // ③ INSERT knowledge_complete_log
        KnowledgeInfoDO knowledge = knowledgeInfoMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getId, knowledgeId)
                        .eq(KnowledgeInfoDO::getUserId, userId)
        );
        if (knowledge == null) {
            throw new BizException(BizErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        KnowledgeCompleteLogDO logDO = new KnowledgeCompleteLogDO();
        logDO.setUserId(userId);
        logDO.setKnowledgeId(knowledgeId);
        logDO.setSuggestionId(suggestionId);
        logDO.setOldContent(knowledge.getKnowledgeContent());
        logDO.setNewContent(suggestion.getSuggestionContent());
        logDO.setCompleteReason(suggestion.getSuggestionReason());
        logDO.setOperator("USER");
        logDO.setIsDeleted(0);
        logDO.setCreateTime(LocalDateTime.now());
        knowledgeCompleteLogMapper.insert(logDO);

        // ④ UPDATE knowledge_info
        knowledge.setCompleteContent(suggestion.getSuggestionContent());
        knowledge.setIsCompleted(1);
        BigDecimal currentScore = knowledge.getMasteryScore() != null ? knowledge.getMasteryScore() : BigDecimal.ZERO;
        BigDecimal newScore = currentScore.add(new BigDecimal("10")).min(new BigDecimal("100"));
        knowledge.setMasteryScore(newScore);
        knowledge.setMasteryLevel(calcMasteryLevel(newScore));
        knowledge.setUpdateTime(LocalDateTime.now());
        knowledgeInfoMapper.updateById(knowledge);

        // ⑤ invalidateCache — 移出事务，防止Redis失败导致DB回滚
        try {
            knowledgeStarMapService.invalidateCache(userId);
        } catch (Exception e) {
            log.warn("星图缓存失效失败（不影响主流程）: userId={}, err={}", userId, e.getMessage());
        }

        // ⑥ 清除检索缓存，避免采纳后搜索列表仍命中旧缓存导致补全内容不更新
        try {
            Set<String> keys = stringRedisTemplate.keys(RedisKeyBuilder.searchResult(userId, "*"));
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清除检索缓存失败（不影响主流程）: userId={}, err={}", userId, e.getMessage());
        }
    }

    @Override
    public void rejectSuggestion(Long suggestionId, Long userId) {
        KnowledgeSuggestionDO suggestion = knowledgeSuggestionMapper.selectById(suggestionId);
        if (suggestion == null || !userId.equals(suggestion.getUserId())) {
            throw new BizException(BizErrorCode.SUGGESTION_NOT_FOUND);
        }
        int affected = knowledgeSuggestionMapper.update(null, new UpdateWrapper<KnowledgeSuggestionDO>()
                .set("status", 2)
                .eq("id", suggestionId)
                .eq("user_id", userId)
                .eq("status", 0)
        );
        if (affected == 0) {
            throw new BizException(BizErrorCode.SUGGESTION_ALREADY_PROCESSED);
        }
    }

    @Override
    public KnowledgeSuggestionVO getSuggestionById(Long suggestionId, Long userId) {
        KnowledgeSuggestionDO suggestion = knowledgeSuggestionMapper.selectById(suggestionId);
        if (suggestion == null || !userId.equals(suggestion.getUserId())) {
            return null;
        }
        return toVO(suggestion);
    }

    @Override
    public List<KnowledgeSuggestionVO> listSuggestions(Long knowledgeId, Long userId, int page, int pageSize, Integer status) {
        LambdaQueryWrapper<KnowledgeSuggestionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeSuggestionDO::getUserId, userId)
                .eq(KnowledgeSuggestionDO::getKnowledgeId, knowledgeId);
        if (status != null) {
            wrapper.eq(KnowledgeSuggestionDO::getStatus, status);
        }
        wrapper.orderByDesc(KnowledgeSuggestionDO::getCreateTime);

        List<KnowledgeSuggestionDO> all = knowledgeSuggestionMapper.selectList(wrapper);
        int total = all.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        if (from >= total) return List.of();

        // DB级分页：避免内存分页导致OOM
        LambdaQueryWrapper<KnowledgeSuggestionDO> pageWrapper = new LambdaQueryWrapper<>();
        pageWrapper.eq(KnowledgeSuggestionDO::getUserId, userId)
                .eq(KnowledgeSuggestionDO::getKnowledgeId, knowledgeId);
        if (status != null) {
            pageWrapper.eq(KnowledgeSuggestionDO::getStatus, status);
        }
        pageWrapper.orderByDesc(KnowledgeSuggestionDO::getCreateTime)
                .last("LIMIT " + pageSize + " OFFSET " + from);

        List<KnowledgeSuggestionDO> paged = knowledgeSuggestionMapper.selectList(pageWrapper);
        return paged.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Boolean getEnableAutoSuggestion(Long userId) {
        return userConfigService.getConfig(userId).getEnableAutoKnowledgeSuggestion();
    }

    @Override
    public void setEnableAutoSuggestion(Long userId, Boolean enabled) {
        com.knowledgestarmap.dto.UserConfigDTO dto = new com.knowledgestarmap.dto.UserConfigDTO();
        dto.setEnableAutoKnowledgeSuggestion(enabled);
        userConfigService.updateConfig(userId, dto);
    }

    private KnowledgeSuggestionVO toVO(KnowledgeSuggestionDO s) {
        KnowledgeSuggestionVO vo = new KnowledgeSuggestionVO();
        vo.setId(s.getId());
        vo.setKnowledgeId(s.getKnowledgeId());
        vo.setSuggestionType(s.getSuggestionType());
        vo.setSuggestionTitle(s.getSuggestionTitle());
        vo.setSuggestionContent(s.getSuggestionContent());
        vo.setSuggestionReason(s.getSuggestionReason());
        vo.setStatus(s.getStatus());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }

    private String generateTitle(Integer type, KnowledgeInfoDO knowledge) {
        switch (type) {
            case 1: return "内容补全建议：" + knowledge.getKnowledgeName();
            case 2: return "漏洞标注：" + knowledge.getKnowledgeName();
            case 3: return "时效更新：" + knowledge.getKnowledgeName();
            default: return "补全建议：" + knowledge.getKnowledgeName();
        }
    }

    private String generateReason(Integer type, KnowledgeInfoDO knowledge) {
        return switch (type) {
            case 1 -> "知识点内容较短，建议补充核心概念、原理及实践示例。";
            case 2 -> "知识点掌握度较低，建议补充相关知识点形成知识体系。";
            case 3 -> "知识点可能已过时，建议核实最新技术规范和最佳实践。";
            default -> "该知识点建议补充完善。";
        };
    }

    private static final String SUGGESTION_SYSTEM_PROMPT =
            "你是知识体系补全助手。根据用户消息中的任务要求，对知识点做针对性补全，输出补全后的完整内容与一句话补全理由。\n" +
            "严格遵守用户消息中的字数与格式约束。只返回一个 JSON 对象，不要包含任何其他内容，格式：\n" +
            "{\"suggestionContent\":\"补全后的完整知识点内容\",\"suggestionReason\":\"一句话补全理由\"}";

    private SuggestionGen tryGenerateByLlm(Integer type, KnowledgeInfoDO knowledge) {
        SuggestionGen gen = new SuggestionGen();
        try {
            String json = chatLlmService.chat(SUGGESTION_SYSTEM_PROMPT,
                    buildSuggestionUserMessage(type, knowledge), 0.5, 500);
            if (json == null || json.isBlank()) {
                log.warn("补全建议LLM返回为空: knowledgeId={}", knowledge.getId());
                return gen;
            }
            JSONObject obj = JSON.parseObject(stripFence(json));
            if (obj == null) {
                log.warn("补全建议LLM返回非JSON无法解析: knowledgeId={}, body={}",
                        knowledge.getId(), json.substring(0, Math.min(200, json.length())));
                return gen;
            }
            String content = obj.getString("suggestionContent");
            String reason = obj.getString("suggestionReason");
            if (content == null || content.isBlank()) {
                log.warn("补全建议LLM返回缺少suggestionContent: knowledgeId={}", knowledge.getId());
                return gen;
            }
            gen.content = content.trim();
            if (reason != null && !reason.isBlank()) {
                gen.reason = reason.trim();
            }
        } catch (Exception e) {
            log.warn("补全建议LLM生成异常: knowledgeId={}, err={}", knowledge.getId(), e.getMessage());
        }
        return gen;
    }

    private String buildSuggestionUserMessage(Integer type, KnowledgeInfoDO k) {
        String typeTask = switch (type) {
            case 1 -> "内容补全：原文信息量偏少，请补充 2~3 个最关键要点（核心概念/关键步骤/实践要点），使内容完整充实。";
            case 2 -> "漏洞标注：请找出原文遗漏的 1~3 个关键盲点（前置知识/易混淆概念/关联实践），逐点补齐，形成完整体系。";
            case 3 -> "时效更新：请识别原文中过时的技术或规范，替换为当前最新、最主流的最佳实践。";
            default -> "内容补全：请补充原文缺失的关键信息，使内容完整。";
        };

        String content = nullToEmpty(k.getKnowledgeContent());
        int limit = Math.min(400, Math.max(200, (int) Math.ceil(content.length() * 1.5)));

        StringBuilder sb = new StringBuilder();
        sb.append("任务：").append(typeTask).append("\n\n")
          .append("知识点名称：").append(nullToEmpty(k.getKnowledgeName())).append("\n")
          .append("所属领域：").append(nullToEmpty(k.getKnowledgeDomain())).append("\n")
          .append("标签：").append(nullToEmpty(k.getKnowledgeTag())).append("\n")
          .append("原文（").append(content.length()).append("字）：\n").append(content).append("\n");

        String existing = k.getCompleteContent();
        if (existing != null && !existing.isBlank()) {
            sb.append("\n已有补全内容（").append(existing.length()).append("字）：\n").append(existing).append("\n")
              .append("注意：在已有补全内容基础上「精简整合」，去重、合并同类项，不要简单追加导致内容膨胀。");
        }

        sb.append("\n\n硬性要求：\n")
          .append("1. 输出「补全后的完整内容」，总字数不超过 ").append(limit).append(" 字；\n")
          .append("2. 用 2~4 个短句分点表达（每点一句，用换行或「①②③」分隔），不要写成整段长文；\n")
          .append("3. 只保留最关键、最确定的信息，不堆砌、不列清单、不编造原文未提及的细节；\n")
          .append("4. suggestionReason 控制在 25 字以内的一句话。");
        return sb.toString();
    }

    private String stripFence(String text) {
        String t = text.trim();
        int fenceStart = t.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = t.indexOf('\n', fenceStart);
            if (contentStart >= 0) {
                int fenceEnd = t.indexOf("```", contentStart);
                if (fenceEnd >= 0) {
                    t = t.substring(contentStart + 1, fenceEnd).trim();
                }
            }
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            t = t.substring(start, end + 1);
        }
        return t;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static class SuggestionGen {
        String content;
        String reason;
    }

    private int calcMasteryLevel(BigDecimal score) {
        if (score == null) return 1;
        if (score.compareTo(new BigDecimal("90")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("70")) >= 0) return 2;
        return 1;
    }
}
