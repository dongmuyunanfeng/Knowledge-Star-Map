package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.KnowledgeQueryDTO;
import com.knowledgestarmap.service.KnowledgeQueryService;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class KnowledgeQueryTool implements AgentTool {

    private static final String NAME = "knowledge_query";
    private static final String DESCRIPTION = "检索用户知识库中的知识点，支持关键词、标签、掌握度筛选。返回匹配的知识点列表及总数。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"检索关键词（模糊匹配名称/内容/标签）\"},\"tag\":{\"type\":\"string\",\"description\":\"知识标签筛选\"},\"masteryLevel\":{\"type\":\"integer\",\"enum\":[1,2,3],\"description\":\"掌握度筛选：1入门 2熟练 3精通\"},\"page\":{\"type\":\"integer\",\"description\":\"页码，默认1\",\"default\":1},\"pageSize\":{\"type\":\"integer\",\"description\":\"每页数量，默认20\",\"default\":20}},\"required\":[]}";

    private final KnowledgeQueryService knowledgeQueryService;

    public KnowledgeQueryTool(KnowledgeQueryService knowledgeQueryService) {
        this.knowledgeQueryService = knowledgeQueryService;
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
            KnowledgeQueryDTO dto = new KnowledgeQueryDTO();
            dto.setKeyword((String) parameters.get("keyword"));
            dto.setTag((String) parameters.get("tag"));
            Object masteryObj = parameters.get("masteryLevel");
            if (masteryObj instanceof Number) {
                dto.setMasteryLevel(((Number) masteryObj).intValue());
            }
            Object pageObj = parameters.get("page");
            if (pageObj instanceof Number) {
                dto.setPage(((Number) pageObj).intValue());
            }
            Object pageSizeObj = parameters.get("pageSize");
            if (pageSizeObj instanceof Number) {
                dto.setPageSize(((Number) pageSizeObj).intValue());
            }

            PageResult<KnowledgeInfoVO> pageResult = knowledgeQueryService.search(dto, userId);

            List<Map<String, Object>> items = new ArrayList<>();
            for (KnowledgeInfoVO vo : pageResult.getList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", vo.getId());
                item.put("knowledgeName", vo.getKnowledgeName());
                item.put("knowledgeTag", vo.getKnowledgeTag());
                item.put("masteryLevel", vo.getMasteryLevel());
                item.put("masteryScore", vo.getMasteryScore());
                item.put("knowledgeContent", vo.getKnowledgeContent());
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalCount", pageResult.getTotal());
            result.put("items", items);

            return ToolExecutionResult.success(result);

        } catch (Exception e) {
            log.error("知识库查询工具执行失败: userId={}", userId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
