package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.CreateDomainDTO;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final KnowledgeStarMapService knowledgeStarMapService;

    public DomainController(KnowledgeStarMapService knowledgeStarMapService) {
        this.knowledgeStarMapService = knowledgeStarMapService;
    }

    @GetMapping
    public Result<Map<String, Object>> listDomains() {
        Long userId = UserContext.getUserId();
        return Result.ok(Map.of("domainNames", knowledgeStarMapService.listDomains(userId)
                .stream()
                .map(KnowledgeStarMapVO::getDomainName)
                .toList()));
    }

    @PostMapping
    public Result<Void> createDomain(@RequestBody CreateDomainDTO dto) {
        if (dto.getDomainName() == null || dto.getDomainName().isBlank()) {
            return Result.fail(400, "领域名称不能为空");
        }
        Long userId = UserContext.getUserId();
        knowledgeStarMapService.createDomain(userId, dto.getDomainName());
        return Result.ok();
    }
}
