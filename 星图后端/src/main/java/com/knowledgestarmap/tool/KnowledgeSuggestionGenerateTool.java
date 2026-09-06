package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.service.KnowledgeCompletionService;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeSuggestionGenerateTool implements AgentTool {

    private static final String NAME = "knowledge_suggestion_generate";
    private static final String DESCRIPTION = "为指定知识点生成AI补全建议。不直接修改知识库，仅创建待确认建议记录（status=0）。用户必须通过前端手动确认后才能落地。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"knowledgeId\":{\"type\":\"integer\",\"description\":\"知识点ID（必填）\"},\"suggestionType\":{\"type\":\"integer\",\"enum\":[1,2,3],\"description\":\"建议类型：1-内容补全 2-漏洞标注 3-时效更新（必填）\"}},\"required\":[\"knowledgeId\",\"suggestionType\"]}";

    private final KnowledgeCompletionService knowledgeCompletionService;

    public KnowledgeSuggestionGenerateTool(KnowledgeCompletionService knowledgeCompletionService) {
        this.knowledgeCompletionService = knowledgeCompletionService;
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
            Object knowledgeIdObj = parameters.get("knowledgeId");
            if (!(knowledgeIdObj instanceof Number)) {
                return ToolExecutionResult.failure("knowledgeId 参数缺失或类型错误");
            }

            Object suggestionTypeObj = parameters.get("suggestionType");
            if (!(suggestionTypeObj instanceof Number)) {
                return ToolExecutionResult.failure("suggestionType 参数缺失或类型错误");
            }

            Long knowledgeId = ((Number) knowledgeIdObj).longValue();
            Integer suggestionType = ((Number) suggestionTypeObj).intValue();

            KnowledgeSuggestionVO vo = knowledgeCompletionService.generateSuggestion(knowledgeId, suggestionType, userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("suggestionId", vo.getId());
            result.put("suggestionTitle", vo.getSuggestionTitle());
            result.put("suggestionContent", vo.getSuggestionContent());
            result.put("suggestionReason", vo.getSuggestionReason());
            result.put("status", vo.getStatus());
            result.put("createTime", vo.getCreateTime());

            return ToolExecutionResult.success(result);

        } catch (Exception e) {
            log.error("补全建议生成工具执行失败: userId={}, parameters={}", userId, parameters, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
