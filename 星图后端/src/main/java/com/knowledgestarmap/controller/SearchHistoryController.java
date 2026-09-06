package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.SearchHistoryService;
import com.knowledgestarmap.vo.SearchHistoryVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search/history")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping
    public Result<PageResult<SearchHistoryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.getUserId();
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        return Result.ok(searchHistoryService.listHistory(userId, page, pageSize));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        searchHistoryService.deleteHistory(id, userId);
        return Result.ok();
    }
}
