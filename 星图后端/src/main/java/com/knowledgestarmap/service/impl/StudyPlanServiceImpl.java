package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.StudyPlanCreateDTO;
import com.knowledgestarmap.dto.StudyPlanUpdateDTO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.KnowledgeStarMapDO;
import com.knowledgestarmap.entity.StudyPlanDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.KnowledgeStarMapMapper;
import com.knowledgestarmap.mapper.StudyPlanMapper;
import com.knowledgestarmap.service.StudyPlanService;
import com.knowledgestarmap.vo.StudyPlanVO;
import com.knowledgestarmap.vo.WaitKnowledgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StudyPlanServiceImpl implements StudyPlanService {

    private static final int TARGET_NEED_MIN_LEN = 10;
    private static final int TARGET_NEED_MAX_LEN = 200;
    private static final int MAX_WAIT_KNOWLEDGE_COUNT = 50;
    private static final int MAX_WAIT_KNOWLEDGE_JSON_LEN = 10000;
    private static final int DEFAULT_PRIORITY = 2;
    private static final int CONTEXT_CHAR_BUDGET = 6000;
    private static final int MAX_CONTEXT_CANDIDATES = 300;
    private static final int PLAN_MAX_TOKENS = 8192;

    private static final String PLAN_SYSTEM_PROMPT =
            "你是学习规划助手。根据用户当前的知识掌握情况与目标需求，生成未来应学习的内容，形成个性化学习规划。只返回一个JSON对象，不要包含其他内容，格式：\n" +
            "{\"planDesc\":\"计划描述文字\",\"waitKnowledge\":[{\"name\":\"知识点名称\",\"tag\":\"所属领域\",\"priority\":1到3的整数,\"schedule\":\"建议学习时间段\",\"learnContent\":\"学什么：该知识点要掌握的具体内容与要点，1-2句话\"}]}\n" +
            "要求：waitKnowledge 每项都是未来要学习的内容；不得重复用户已熟练/精通掌握的知识点；可针对用户入门未掌握的点给出补强建议，也可按目标需求补充全新知识点；按学习先后顺序排列，priority 1为最高优先级；schedule 为建议学习时间段，必须严格按用户消息中「计划周期」给出的总天数 N 自适应分配：N≤14 用「第X天」，15≤N≤60 用「第X-Y天」，N>60 用「第X周」，相邻知识点的时间段要连续不重叠，并完整覆盖整个计划周期；learnContent 要具体可执行，写清学什么、达到什么程度。";

    private final StudyPlanMapper studyPlanMapper;
    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final KnowledgeStarMapMapper knowledgeStarMapMapper;
    private final ChatLlmService chatLlmService;

    public StudyPlanServiceImpl(StudyPlanMapper studyPlanMapper,
                                KnowledgeInfoMapper knowledgeInfoMapper,
                                KnowledgeStarMapMapper knowledgeStarMapMapper,
                                ChatLlmService chatLlmService) {
        this.studyPlanMapper = studyPlanMapper;
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.knowledgeStarMapMapper = knowledgeStarMapMapper;
        this.chatLlmService = chatLlmService;
    }

    @Override
    public StudyPlanVO createPlan(StudyPlanCreateDTO dto, Long userId) {
        if (dto.getPlanTitle() == null || dto.getPlanTitle().isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "计划标题不能为空");
        }
        validateTargetNeed(dto.getTargetNeed());

        StudyPlanDO plan = new StudyPlanDO();
        plan.setUserId(userId);
        plan.setPlanTitle(dto.getPlanTitle());
        plan.setPlanType(dto.getPlanType());
        plan.setPlanDesc(dto.getPlanDesc());
        plan.setTargetNeed(dto.getTargetNeed());
        plan.setWaitKnowledge("[]");
        plan.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        plan.setFinishStatus(0);
        plan.setProgressRate(BigDecimal.ZERO);

        studyPlanMapper.insert(plan);

        return toVO(plan);
    }

    @Override
    public StudyPlanVO generatePlan(StudyPlanCreateDTO dto, Long userId) {
        validateTargetNeed(dto.getTargetNeed());

        int effectiveDays = dto.getPlanDays() != null && dto.getPlanDays() > 0
                ? dto.getPlanDays() : defaultDays(dto.getPlanType());
        String title = (dto.getPlanTitle() == null || dto.getPlanTitle().isBlank())
                ? generatePlanTitle(dto.getTargetNeed(), effectiveDays)
                : dto.getPlanTitle().trim();

        List<KnowledgeInfoDO> knowledgeContext = queryKnowledgeContext(userId, dto.getTargetNeed());

        String planDesc;
        List<Map<String, Object>> waitKnowledge;
        try {
            String llmJson = chatLlmService.chatStream(
                    PLAN_SYSTEM_PROMPT,
                    buildPlanUserMessage(dto.getTargetNeed(), dto.getPlanType(), effectiveDays, knowledgeContext),
                    0.3, PLAN_MAX_TOKENS, null);
            PlanGenResult gen = parsePlanGenResult(llmJson);
            planDesc = gen.planDesc;
            waitKnowledge = gen.waitKnowledge;
        } catch (Exception e) {
            log.warn("学习规划LLM生成失败: userId={}, err={}", userId, e.getMessage());
            throw new BizException(BizErrorCode.LLM_TIMEOUT, "学习规划生成失败，请稍后重试");
        }
        if (waitKnowledge.isEmpty()) {
            throw new BizException(BizErrorCode.LLM_TIMEOUT, "学习规划生成失败，请稍后重试");
        }

        String waitKnowledgeJson = JSON.toJSONString(waitKnowledge);
        if (waitKnowledgeJson.length() > MAX_WAIT_KNOWLEDGE_JSON_LEN) {
            waitKnowledgeJson = waitKnowledgeJson.substring(0, MAX_WAIT_KNOWLEDGE_JSON_LEN);
        }

        StudyPlanDO plan = new StudyPlanDO();
        plan.setUserId(userId);
        plan.setPlanType(dto.getPlanType());
        plan.setTargetNeed(dto.getTargetNeed());
        plan.setPlanTitle(title);
        plan.setPlanDesc(dto.getPlanDesc() != null && !dto.getPlanDesc().isBlank() ? dto.getPlanDesc() : planDesc);
        plan.setWaitKnowledge(waitKnowledgeJson);
        plan.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        plan.setFinishStatus(0);
        plan.setProgressRate(BigDecimal.ZERO);

        studyPlanMapper.insert(plan);
        return toVO(plan);
    }

    @Override
    public PageResult<StudyPlanVO> listPlans(Long userId, int page, int pageSize) {
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));

        Page<StudyPlanDO> mpPage = studyPlanMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getUserId, userId)
                        .orderByDesc(StudyPlanDO::getCreateTime)
        );

        List<StudyPlanVO> voList = mpPage.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(mpPage.getTotal(), page, pageSize, voList);
    }

    @Override
    public StudyPlanVO getPlan(Long planId, Long userId) {
        StudyPlanDO plan = studyPlanMapper.selectOne(
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getId, planId)
                        .eq(StudyPlanDO::getUserId, userId)
        );
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_FOUND);
        }
        return toVO(plan);
    }

    @Override
    public StudyPlanVO updatePlan(Long planId, Long userId, StudyPlanUpdateDTO dto) {
        StudyPlanDO plan = studyPlanMapper.selectOne(
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getId, planId)
                        .eq(StudyPlanDO::getUserId, userId)
        );
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_FOUND);
        }

        if (dto.getPlanTitle() != null) {
            plan.setPlanTitle(dto.getPlanTitle());
        }
        if (dto.getPlanDesc() != null) {
            plan.setPlanDesc(dto.getPlanDesc());
        }
        if (dto.getPriority() != null) {
            plan.setPriority(dto.getPriority());
        }

        studyPlanMapper.updateById(plan);
        return toVO(plan);
    }

    @Override
    public void deletePlan(Long planId, Long userId) {
        StudyPlanDO plan = studyPlanMapper.selectOne(
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getId, planId)
                        .eq(StudyPlanDO::getUserId, userId)
        );
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_FOUND);
        }
        studyPlanMapper.deleteById(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshProgress(Long planId, Long userId) {
        StudyPlanDO plan = studyPlanMapper.selectOne(
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getId, planId)
                        .eq(StudyPlanDO::getUserId, userId)
        );
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_FOUND);
        }
        List<Map<String, Object>> waitKnowledge = parseWaitKnowledge(plan.getWaitKnowledge());

        if (waitKnowledge.isEmpty()) {
            plan.setProgressRate(BigDecimal.valueOf(100));
            plan.setFinishStatus(1);
            studyPlanMapper.updateById(plan);
            return;
        }

        int originalTotal = waitKnowledge.size();
        waitKnowledge.removeIf(item -> isLearned(item, userId));

        plan.setWaitKnowledge(JSON.toJSONString(waitKnowledge));

        if (originalTotal == 0) {
            plan.setProgressRate(BigDecimal.valueOf(100));
            plan.setFinishStatus(1);
        } else {
            int remaining = waitKnowledge.size();
            int learned = originalTotal - remaining;
            BigDecimal rate = BigDecimal.valueOf((double) learned * 100 / originalTotal)
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.valueOf(100));
            plan.setProgressRate(rate);
            plan.setFinishStatus(rate.compareTo(BigDecimal.valueOf(100)) == 0 ? 1 : 0);
        }

        studyPlanMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePlan(Long planId, Long userId) {
        StudyPlanDO plan = studyPlanMapper.selectOne(
                new LambdaQueryWrapper<StudyPlanDO>()
                        .eq(StudyPlanDO::getId, planId)
                        .eq(StudyPlanDO::getUserId, userId)
        );
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_FOUND);
        }
        plan.setFinishStatus(1);
        plan.setProgressRate(BigDecimal.valueOf(100));
        studyPlanMapper.updateById(plan);
    }

    private List<Map<String, Object>> parseWaitKnowledge(String waitKnowledgeJson) {
        if (waitKnowledgeJson == null || waitKnowledgeJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = JSON.parseObject(
                    waitKnowledgeJson,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            if (list != null && list.size() > MAX_WAIT_KNOWLEDGE_COUNT) {
                list = list.subList(0, MAX_WAIT_KNOWLEDGE_COUNT);
            }
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            log.warn("waitKnowledge JSON 反序列化失败，视为空数组: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean isLearned(Map<String, Object> item, Long userId) {
        Object idObj = item.get("id");
        if (idObj instanceof Number) {
            Long knowledgeId = ((Number) idObj).longValue();
            KnowledgeInfoDO knowledge = knowledgeInfoMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeInfoDO>()
                            .eq(KnowledgeInfoDO::getId, knowledgeId)
                            .eq(KnowledgeInfoDO::getUserId, userId)
            );
            return knowledge != null && knowledge.getMasteryLevel() != null && knowledge.getMasteryLevel() >= 2;
        }
        Object nameObj = item.get("name");
        if (nameObj instanceof String) {
            String name = ((String) nameObj).trim();
            if (name.isEmpty()) {
                return false;
            }
            Long count = knowledgeInfoMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeInfoDO>()
                            .eq(KnowledgeInfoDO::getUserId, userId)
                            .eq(KnowledgeInfoDO::getKnowledgeName, name)
                            .ge(KnowledgeInfoDO::getMasteryLevel, 2)
            );
            return count != null && count > 0;
        }
        return false;
    }

    private void validateTargetNeed(String targetNeed) {
        if (targetNeed == null || targetNeed.isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "targetNeed 不能为空");
        }
        if (targetNeed.length() < TARGET_NEED_MIN_LEN || targetNeed.length() > TARGET_NEED_MAX_LEN) {
            throw new BizException(BizErrorCode.PARAM_ERROR,
                    "targetNeed 长度必须在 " + TARGET_NEED_MIN_LEN + "-" + TARGET_NEED_MAX_LEN + " 字符之间");
        }
    }

    private StudyPlanVO toVO(StudyPlanDO plan) {
        StudyPlanVO vo = new StudyPlanVO();
        vo.setId(plan.getId());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanType(plan.getPlanType());
        vo.setPlanDesc(plan.getPlanDesc());
        vo.setTargetNeed(plan.getTargetNeed());
        vo.setPriority(plan.getPriority());
        vo.setFinishStatus(plan.getFinishStatus());
        vo.setProgressRate(plan.getProgressRate());
        vo.setCreateTime(plan.getCreateTime());
        vo.setWaitKnowledge(parseWaitKnowledgeToVOList(plan.getWaitKnowledge()));
        return vo;
    }

    private List<WaitKnowledgeVO> parseWaitKnowledgeToVOList(String waitKnowledgeJson) {
        if (waitKnowledgeJson == null || waitKnowledgeJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = JSON.parseObject(
                    waitKnowledgeJson,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            if (list == null) {
                return new ArrayList<>();
            }
            return list.stream().map(item -> {
                WaitKnowledgeVO vo = new WaitKnowledgeVO();
                Object idObj = item.get("id");
                vo.setId(idObj instanceof Number ? ((Number) idObj).longValue() : null);
                vo.setName((String) item.get("name"));
                vo.setTag((String) item.get("tag"));
                vo.setSchedule((String) item.get("schedule"));
                vo.setLearnContent((String) item.get("learnContent"));
                Object priorityObj = item.get("priority");
                vo.setPriority(priorityObj instanceof Number ? ((Number) priorityObj).intValue() : null);
                return vo;
            }).toList();
        } catch (Exception e) {
            log.warn("waitKnowledge 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<KnowledgeInfoDO> queryKnowledgeContext(Long userId, String targetNeed) {
        List<KnowledgeInfoDO> all = knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .ge(KnowledgeInfoDO::getMasteryLevel, 1)
        );

        Set<String> weakDomains = knowledgeStarMapMapper.selectList(
                new LambdaQueryWrapper<KnowledgeStarMapDO>()
                        .eq(KnowledgeStarMapDO::getUserId, userId)
                        .eq(KnowledgeStarMapDO::getWeakFlag, 1)
        ).stream()
                .map(KnowledgeStarMapDO::getDomainName)
                .filter(d -> d != null && !d.isBlank())
                .collect(Collectors.toSet());

        List<KnowledgeInfoDO> layerA = new ArrayList<>();
        List<KnowledgeInfoDO> layerB = new ArrayList<>();
        List<KnowledgeInfoDO> layerC = new ArrayList<>();

        for (KnowledgeInfoDO k : all) {
            if (relevanceScore(targetNeed, k) > 0) {
                layerA.add(k);
            } else if (k.getMasteryLevel() != null && k.getMasteryLevel() <= 1) {
                layerB.add(k);
            } else if (k.getKnowledgeDomain() != null && weakDomains.contains(k.getKnowledgeDomain())) {
                layerC.add(k);
            }
        }

        layerA.sort(Comparator
                .comparingInt((KnowledgeInfoDO k) -> k.getMasteryLevel() == null ? 2 : k.getMasteryLevel())
                .thenComparing((KnowledgeInfoDO k) -> k.getMasteryScore() == null ? new BigDecimal("100") : k.getMasteryScore()));
        layerB.sort(Comparator.comparing((KnowledgeInfoDO k) -> k.getMasteryScore() == null ? new BigDecimal("100") : k.getMasteryScore()));
        layerC.sort(Comparator.comparing((KnowledgeInfoDO k) -> k.getMasteryScore() == null ? new BigDecimal("100") : k.getMasteryScore()));

        List<KnowledgeInfoDO> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (KnowledgeInfoDO k : layerA) addIfAbsent(ordered, seen, k);
        for (KnowledgeInfoDO k : layerB) addIfAbsent(ordered, seen, k);
        for (KnowledgeInfoDO k : layerC) addIfAbsent(ordered, seen, k);
        return ordered;
    }

    private void addIfAbsent(List<KnowledgeInfoDO> ordered, Set<Long> seen, KnowledgeInfoDO k) {
        if (ordered.size() >= MAX_CONTEXT_CANDIDATES) return;
        if (k.getId() != null && seen.add(k.getId())) {
            ordered.add(k);
        }
    }

    private int relevanceScore(String targetNeed, KnowledgeInfoDO k) {
        if (targetNeed == null || targetNeed.isBlank()) return 0;
        String haystack = (nullToEmpty(k.getKnowledgeName()) + " "
                + nullToEmpty(k.getKnowledgeTag()) + " "
                + nullToEmpty(k.getKnowledgeDomain())).toLowerCase();
        String needle = targetNeed.toLowerCase();
        if (needle.isBlank()) return 0;
        if (haystack.contains(needle)) return 100;
        int score = 0;
        for (String term : extractTerms(needle)) {
            if (term.length() >= 2 && haystack.contains(term)) score++;
        }
        return score;
    }

    private Set<String> extractTerms(String text) {
        Set<String> terms = new HashSet<>();
        java.util.regex.Matcher latin = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9]+").matcher(text);
        while (latin.find()) {
            terms.add(latin.group().toLowerCase());
        }
        StringBuilder cjk = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isIdeographic(c)) cjk.append(c);
        }
        String c = cjk.toString();
        for (int i = 0; i + 1 < c.length(); i++) {
            terms.add(c.substring(i, i + 2));
        }
        return terms;
    }

    private String buildPlanUserMessage(String targetNeed, Integer planType, int effectiveDays, List<KnowledgeInfoDO> knowledgeContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户目标需求：").append(targetNeed).append("\n");
        sb.append("计划周期：共 ").append(effectiveDays).append(" 天（周期档位 ").append(planType).append("：1短期/2中期/3长期）\n");
        sb.append("当前知识掌握情况（格式：名称 | 摘要 | 标签 | 领域 | 掌握度1入门2熟练3精通 | 掌握分）：\n");
        if (knowledgeContext.isEmpty()) {
            sb.append("（无）\n");
        } else {
            boolean first = true;
            for (KnowledgeInfoDO k : knowledgeContext) {
                String line = "- " + nullToEmpty(k.getKnowledgeName())
                        + " | " + summarize(k.getKnowledgeContent())
                        + " | " + nullToEmpty(k.getKnowledgeTag())
                        + " | " + nullToEmpty(k.getKnowledgeDomain())
                        + " | " + (k.getMasteryLevel() == null ? 2 : k.getMasteryLevel())
                        + " | " + (k.getMasteryScore() == null ? "" : k.getMasteryScore())
                        + "\n";
                if (!first && sb.length() + line.length() > CONTEXT_CHAR_BUDGET) {
                    break;
                }
                sb.append(line);
                first = false;
            }
        }
        sb.append("\n请基于上述掌握情况与目标需求，生成未来应学习的内容（入门点的补强建议 + 面向需求的新增知识点），输出学习规划。");
        return sb.toString();
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) return "";
        String c = content.trim().replace('\n', ' ').replace('\r', ' ');
        int max = 50;
        return c.length() <= max ? c : c.substring(0, max) + "…";
    }

    private PlanGenResult parsePlanGenResult(String llmJson) {
        PlanGenResult r = new PlanGenResult();
        if (llmJson == null || llmJson.isBlank()) {
            return r;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(stripFence(llmJson));
        } catch (Exception e) {
            log.warn("学习规划LLM返回JSON解析失败: {}", e.getMessage());
            return r;
        }
        if (obj == null) {
            return r;
        }
        String desc = obj.getString("planDesc");
        r.planDesc = desc != null ? desc : "";

        Set<String> seen = new HashSet<>();
        JSONArray arr = obj.getJSONArray("waitKnowledge");
        if (arr != null) {
            for (int i = 0; i < arr.size() && r.waitKnowledge.size() < MAX_WAIT_KNOWLEDGE_COUNT; i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.getString("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                name = name.trim();
                if (!seen.add(name)) {
                    continue;
                }
                String tag = item.getString("tag");
                String schedule = item.getString("schedule");
                String learnContent = item.getString("learnContent");
                Integer priorityObj = item.getInteger("priority");
                int priority = priorityObj != null ? priorityObj : DEFAULT_PRIORITY;
                if (priority < 1 || priority > 3) {
                    priority = DEFAULT_PRIORITY;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("tag", tag);
                entry.put("priority", priority);
                entry.put("schedule", schedule == null ? "" : schedule.trim());
                entry.put("learnContent", learnContent == null ? "" : learnContent.trim());
                r.waitKnowledge.add(entry);
            }
        }
        return r;
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

    private String generatePlanTitle(String targetNeed, int effectiveDays) {
        String need = targetNeed == null ? "" : targetNeed;
        String prefix = need.length() > 15 ? need.substring(0, 15) + "..." : need;
        return effectiveDays + "天学习规划-" + prefix;
    }

    /** planType 默认天数：1短期7天 / 2中期30天 / 3长期90天，非法值按中期30天兜底。 */
    private int defaultDays(Integer planType) {
        return switch (planType == null ? 2 : planType) {
            case 1 -> 7;
            case 3 -> 90;
            default -> 30;
        };
    }

    private static class PlanGenResult {
        String planDesc = "";
        List<Map<String, Object>> waitKnowledge = new ArrayList<>();
    }
}
