package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.entity.KnowledgeSuggestionDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeCompletionService;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/suggestions")
public class KnowledgeSuggestionController {

    private final KnowledgeCompletionService knowledgeCompletionService;

    public KnowledgeSuggestionController(KnowledgeCompletionService knowledgeCompletionService) {
        this.knowledgeCompletionService = knowledgeCompletionService;
    }

    @GetMapping
    public Result<PageResult<KnowledgeSuggestionVO>> list(
            @RequestParam Long knowledgeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {
        Long userId = UserContext.getUserId();
        List<KnowledgeSuggestionVO> suggestions =
                knowledgeCompletionService.listSuggestions(knowledgeId, userId, page, pageSize, status);
        return Result.ok(PageResult.of(suggestions.size(), page, pageSize, suggestions));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeSuggestionVO> getById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        KnowledgeSuggestionVO vo = knowledgeCompletionService.getSuggestionById(id, userId);
        if (vo == null) {
            throw new BizException(BizErrorCode.SUGGESTION_NOT_FOUND);
        }
        return Result.ok(vo);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approveSuggestion(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        knowledgeCompletionService.approveSuggestion(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> rejectSuggestion(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        knowledgeCompletionService.rejectSuggestion(id, userId);
        return Result.ok();
    }
}
