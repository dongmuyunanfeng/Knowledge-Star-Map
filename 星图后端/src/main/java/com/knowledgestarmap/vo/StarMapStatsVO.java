package com.knowledgestarmap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StarMapStatsVO {
    private Integer totalKnowledgeCount;
    private BigDecimal averageMasteryScore;
    /**
     * 扁平结构，与 Agent 工具返回值保持一致。
     * 与 HTTP 接口 GET /api/star-map/domains 共用 KnowledgeStarMapVO，
     * 但 Agent 工具仅使用 domainName/knowledgeCount/masteryScore/weakFlag 字段。
     */
    private List<KnowledgeStarMapVO> domains;
}
