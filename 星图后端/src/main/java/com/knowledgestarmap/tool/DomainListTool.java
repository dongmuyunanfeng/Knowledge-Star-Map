package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DomainListTool implements AgentTool {

    private static final String NAME = "domain_list";
    private static final String DESCRIPTION = "获取当前用户所有知识领域/标签列表（动态，无硬编码）。从knowledge_star_map动态查询。当Agent在文件解析中发现新标签时，调用KnowledgeIngestionService.upsertDomain()自动创建domain记录。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final KnowledgeStarMapService knowledgeStarMapService;

    public DomainListTool(KnowledgeStarMapService knowledgeStarMapService) {
        this.knowledgeStarMapService = knowledgeStarMapService;
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
            List<KnowledgeStarMapVO> domains = knowledgeStarMapService.listDomains(userId);
            List<String> domainNames = domains.stream()
                    .map(KnowledgeStarMapVO::getDomainName)
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domainNames", domainNames);

            return ToolExecutionResult.success(result);
        } catch (Exception e) {
            log.error("domain_list工具执行失败: userId={}", userId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
