package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import com.knowledgestarmap.vo.StarMapStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class StarMapStatsTool implements AgentTool {

    private static final String NAME = "star_map_stats";
    private static final String DESCRIPTION = "获取用户知识星图的统计数据和领域列表，用于Agent分析用户知识体系整体情况。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final KnowledgeStarMapService knowledgeStarMapService;

    public StarMapStatsTool(KnowledgeStarMapService knowledgeStarMapService) {
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
            StarMapStatsVO stats = knowledgeStarMapService.getStats(userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalKnowledgeCount", stats.getTotalKnowledgeCount());
            result.put("averageMasteryScore", stats.getAverageMasteryScore());

            List<KnowledgeStarMapVO> domainList = stats.getDomains();
            List<Map<String, Object>> domains = new ArrayList<>();
            for (KnowledgeStarMapVO item : domainList) {
                Map<String, Object> domain = new LinkedHashMap<>();
                domain.put("domainName", item.getDomainName());
                domain.put("knowledgeCount", item.getKnowledgeCount());
                domain.put("masteryScore", item.getMasteryScore());
                domain.put("weakFlag", item.getWeakFlag());
                domains.add(domain);
            }
            result.put("domains", domains);

            return ToolExecutionResult.success(result);
        } catch (Exception e) {
            log.error("star_map_stats工具执行失败: userId={}", userId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
