package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.service.ResumeService;
import com.knowledgestarmap.vo.ResumeMaterialVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ResumeGenerateTool implements AgentTool {

    private static final String NAME = "resume_generate";
    private static final String DESCRIPTION = "基于用户知识积累和项目信息生成简历素材。生成或更新resume_material记录（upsert by userId）。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"forceRegenerate\":{\"type\":\"boolean\",\"description\":\"是否强制重新生成（忽略缓存）\"},\"targetNeed\":{\"type\":\"string\",\"description\":\"目标岗位或方向，用于筛选相关知识点\"},\"requirement\":{\"type\":\"string\",\"description\":\"个人求职需求/强调点，用于进一步筛选与生成\"}}}";

    private final ResumeService resumeService;

    public ResumeGenerateTool(ResumeService resumeService) {
        this.resumeService = resumeService;
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
            Boolean forceRegenerate = parameters.get("forceRegenerate") instanceof Boolean
                    ? (Boolean) parameters.get("forceRegenerate") : false;
            String targetNeed = parameters.get("targetNeed") instanceof String
                    ? (String) parameters.get("targetNeed") : null;
            String requirement = parameters.get("requirement") instanceof String
                    ? (String) parameters.get("requirement") : null;
            ResumeMaterialVO vo = resumeService.generateResume(userId, forceRegenerate, targetNeed, requirement);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("techStack", vo.getTechStack());
            result.put("skillDesc", vo.getSkillDesc());
            result.put("projectHighlights", vo.getProjectHighlights());
            result.put("resumeSummary", vo.getResumeSummary());

            return ToolExecutionResult.success(result);
        } catch (Exception e) {
            log.error("resume_generate 工具执行失败: userId={}", userId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
