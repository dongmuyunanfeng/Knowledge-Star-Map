package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.KnowledgeQueryDTO;
import com.knowledgestarmap.dto.KnowledgeSuggestionGenerateDTO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeCompletionService;
import com.knowledgestarmap.service.KnowledgeQueryService;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeQueryService knowledgeQueryService;
    private final KnowledgeCompletionService knowledgeCompletionService;

    public KnowledgeController(KnowledgeQueryService knowledgeQueryService,
                               KnowledgeCompletionService knowledgeCompletionService) {
        this.knowledgeQueryService = knowledgeQueryService;
        this.knowledgeCompletionService = knowledgeCompletionService;
    }

    @GetMapping
    public Result<PageResult<KnowledgeInfoVO>> list(KnowledgeQueryDTO dto) {
        Long userId = UserContext.getUserId();
        dto.setPage(Math.max(1, dto.getPage() != null ? dto.getPage() : 1));
        dto.setPageSize(Math.min(100, Math.max(1, dto.getPageSize() != null ? dto.getPageSize() : 20)));
        return Result.ok(knowledgeQueryService.list(dto, userId));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeInfoVO> getById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.ok(knowledgeQueryService.getById(id, userId));
    }

    @PostMapping
    public Result<KnowledgeInfoVO> create(@RequestBody KnowledgeInfoDO knowledgeInfo) {
        Long userId = UserContext.getUserId();
        if (knowledgeInfo.getKnowledgeTag() == null || knowledgeInfo.getKnowledgeTag().isBlank()) {
            throw new BizException(BizErrorCode.KNOWLEDGE_TAG_REQUIRED);
        }
        if (knowledgeInfo.getMasteryLevel() == null) knowledgeInfo.setMasteryLevel(1);
        if (knowledgeInfo.getMasteryScore() == null) knowledgeInfo.setMasteryScore(new java.math.BigDecimal("50.00"));
        if (knowledgeInfo.getIsCompleted() == null) knowledgeInfo.setIsCompleted(0);
        if (knowledgeInfo.getSourceType() == null) knowledgeInfo.setSourceType(2);
        knowledgeInfo.setUserId(userId);
        KnowledgeInfoVO created = knowledgeQueryService.createKnowledge(knowledgeInfo, userId);
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    public Result<KnowledgeInfoVO> update(@PathVariable Long id, @RequestBody KnowledgeInfoDO knowledgeInfo) {
        Long userId = UserContext.getUserId();
        knowledgeInfo.setId(id);
        knowledgeQueryService.updateKnowledge(knowledgeInfo, userId);
        return Result.ok(knowledgeQueryService.getById(id, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        knowledgeQueryService.deleteKnowledge(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/suggestions")
    public Result<KnowledgeSuggestionVO> generateSuggestion(
            @PathVariable Long id,
            @RequestBody KnowledgeSuggestionGenerateDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getKnowledgeId() == null || dto.getSuggestionType() == null) {
            throw new BizException(BizErrorCode.PARAM_ERROR);
        }
        if (!dto.getKnowledgeId().equals(id)) {
            throw new BizException(BizErrorCode.PARAM_ERROR);
        }
        KnowledgeSuggestionVO vo = knowledgeCompletionService.generateSuggestion(
                dto.getKnowledgeId(), dto.getSuggestionType(), userId);
        return Result.ok(vo);
    }

    @GetMapping("/tags")
    public Result<java.util.List<String>> getTags() {
        Long userId = UserContext.getUserId();
        java.util.List<String> tags = knowledgeQueryService.listDistinctTags(userId);
        return Result.ok(tags);
    }

    @GetMapping("/domains")
    public Result<java.util.List<String>> getDomains() {
        Long userId = UserContext.getUserId();
        java.util.List<String> domains = knowledgeQueryService.listDistinctDomains(userId);
        return Result.ok(domains);
    }
}
