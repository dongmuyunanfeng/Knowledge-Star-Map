package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.dto.StudyPlanCreateDTO;
import com.knowledgestarmap.service.StudyPlanService;
import com.knowledgestarmap.vo.StudyPlanVO;
import com.knowledgestarmap.vo.WaitKnowledgeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LearningPlanGenerateTool implements AgentTool {

    private static final String NAME = "learning_plan_generate";
    private static final String DESCRIPTION = "基于用户已掌握的知识与目标需求，生成未来应进一步学习的新知识点，形成个性化学习规划。planType 是默认周期档位（1短期7天 / 2中期30天 / 3长期90天）；当用户明确说出具体天数时，系统会自动按用户天数生成，无需你手动换算。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"planType\":{\"type\":\"integer\",\"enum\":[1,2,3],\"description\":\"默认周期档位：1短期(约7天) 2中期(约30天) 3长期(约90天)，按用户目标的远近程度选择\"},\"targetNeed\":{\"type\":\"string\",\"description\":\"用户目标需求描述，如'准备秋招Java岗面试'\"}},\"required\":[\"planType\",\"targetNeed\"]}";

    private final StudyPlanService studyPlanService;

    public LearningPlanGenerateTool(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String parametersSchema() {
        return PARAMETERS_SCHEMA;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> parameters, Long userId) {
        try {
            Integer planType = parameters.get("planType") instanceof Number
                    ? ((Number) parameters.get("planType")).intValue() : null;
            String targetNeed = (String) parameters.get("targetNeed");
            String userQuery = (String) parameters.get("userQuery");

            // LLM 提取的目标需求过短/缺失时，回退到用户原始提问，保证目标描述充分，避免被长度校验(10-200)拦截
            if (targetNeed == null || targetNeed.isBlank() || targetNeed.trim().length() < 10) {
                targetNeed = userQuery;
            }
            if (targetNeed != null) {
                targetNeed = targetNeed.trim();
                if (targetNeed.length() > 200) {
                    targetNeed = targetNeed.substring(0, 200);
                }
            }

            if (planType == null || targetNeed == null || targetNeed.isBlank()) {
                return ToolExecutionResult.failure("planType 和 targetNeed 不能为空");
            }

            StudyPlanCreateDTO dto = new StudyPlanCreateDTO();
            dto.setPlanType(planType);
            dto.setTargetNeed(targetNeed);

            // 用户明确说出具体天数时，用其替换 planType 默认的 7/30/90，并把档位重算回一致
            Integer days = extractDays(userQuery);
            if (days != null) {
                dto.setPlanDays(days);
                dto.setPlanType(daysToPlanType(days));
            }

            StudyPlanVO vo = studyPlanService.generatePlan(dto, userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("planId", vo.getId());
            result.put("planTitle", vo.getPlanTitle());
            result.put("planDesc", vo.getPlanDesc());
            result.put("waitKnowledge", vo.getWaitKnowledge());
            result.put("progressRate", vo.getProgressRate());

            ToolExecutionResult execution = ToolExecutionResult.success(result);
            execution.setFinalAnswer(buildPlanAnswer(vo));
            return execution;

        } catch (Exception e) {
            log.error("learning_plan_generate 工具执行失败: userId={}", userId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    private String buildPlanAnswer(StudyPlanVO vo) {
        StringBuilder sb = new StringBuilder();
        if (vo.getPlanTitle() != null && !vo.getPlanTitle().isBlank()) {
            sb.append("## ").append(vo.getPlanTitle()).append("\n\n");
        }
        if (vo.getPlanDesc() != null && !vo.getPlanDesc().isBlank()) {
            sb.append(vo.getPlanDesc()).append("\n\n");
        }
        List<WaitKnowledgeVO> items = vo.getWaitKnowledge();
        if (items != null && !items.isEmpty()) {
            for (int p = 1; p <= 3; p++) {
                final int priority = p;
                List<WaitKnowledgeVO> group = items.stream()
                        .filter(k -> k != null && k.getPriority() != null && k.getPriority() == priority)
                        .toList();
                if (group.isEmpty()) {
                    continue;
                }
                sb.append("### ").append(stageLabel(p)).append("\n");
                int idx = 1;
                for (WaitKnowledgeVO k : group) {
                    sb.append(idx++).append(". **").append(k.getName() == null ? "" : k.getName()).append("**");
                    if (k.getTag() != null && !k.getTag().isBlank()) {
                        sb.append("（").append(k.getTag()).append("）");
                    }
                    if (k.getSchedule() != null && !k.getSchedule().isBlank()) {
                        sb.append("【").append(k.getSchedule()).append("】");
                    }
                    if (k.getLearnContent() != null && !k.getLearnContent().isBlank()) {
                        sb.append("：").append(k.getLearnContent());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }
        sb.append("\n规划已保存，可在「学习规划」页查看并跟踪进度。");
        return sb.toString();
    }

    private String stageLabel(int priority) {
        return switch (priority) {
            case 1 -> "第一阶段 · 优先级1（先学）";
            case 2 -> "第二阶段 · 优先级2（进阶）";
            default -> "第三阶段 · 优先级3（巩固）";
        };
    }

    /** 从用户原始提问中提取明确的天数，如「30天」「45 天」「7日」，未命中返回 null。 */
    private Integer extractDays(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,3})\\s*(?:天|日|days?)").matcher(userQuery);
        if (m.find()) {
            try {
                int days = Integer.parseInt(m.group(1));
                if (days > 0 && days <= 366) {
                    return days;
                }
            } catch (NumberFormatException ignored) {
                // 忽略无法解析的天数
            }
        }
        return null;
    }

    /** 天数映射回周期档位：≤7短期(1)、≤30中期(2)、其余长期(3)。 */
    private int daysToPlanType(int days) {
        if (days <= 7) {
            return 1;
        }
        if (days <= 30) {
            return 2;
        }
        return 3;
    }
}
