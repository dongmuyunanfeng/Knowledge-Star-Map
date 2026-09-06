package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.KnowledgeQueryParam;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeSearchService;
import com.knowledgestarmap.service.SearchHistoryService;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import com.knowledgestarmap.vo.SearchHistoryVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;
    private final SearchHistoryService searchHistoryService;

    public KnowledgeSearchController(KnowledgeSearchService knowledgeSearchService,
                                     SearchHistoryService searchHistoryService) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping("/knowledge")
    public Result<PageResult<KnowledgeInfoVO>> search(KnowledgeQueryParam dto) {
        Long userId = UserContext.getUserId();
        dto.setPage(Math.max(1, dto.getPage() != null ? dto.getPage() : 1));
        dto.setPageSize(Math.min(100, Math.max(1, dto.getPageSize() != null ? dto.getPageSize() : 20)));

        PageResult<KnowledgeInfoVO> result = knowledgeSearchService.search(dto, userId);

        try {
            searchHistoryService.addHistory(userId,
                    dto.getKeyword() != null ? dto.getKeyword() : "",
                    (int) result.getTotal());
        } catch (Exception e) {
            // 检索历史写入失败不影响主流程
        }

        return Result.ok(result);
    }
}
