package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import com.knowledgestarmap.vo.ProjectStarMapVO;
import com.knowledgestarmap.vo.StarMapStatsVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/star-map")
public class KnowledgeStarMapController {

    private final KnowledgeStarMapService knowledgeStarMapService;

    public KnowledgeStarMapController(KnowledgeStarMapService knowledgeStarMapService) {
        this.knowledgeStarMapService = knowledgeStarMapService;
    }

    @GetMapping("/domains")
    public Result<List<KnowledgeStarMapVO>> listDomains() {
        Long userId = UserContext.getUserId();
        return Result.ok(knowledgeStarMapService.listDomains(userId));
    }

    @GetMapping("/projects")
    public Result<List<ProjectStarMapVO>> listProjects() {
        Long userId = UserContext.getUserId();
        return Result.ok(knowledgeStarMapService.listProjects(userId));
    }

    @GetMapping("/stats")
    public Result<StarMapStatsVO> getStats() {
        Long userId = UserContext.getUserId();
        return Result.ok(knowledgeStarMapService.getStats(userId));
    }

    @PostMapping("/recalculate")
    public Result<Void> recalculate() {
        Long userId = UserContext.getUserId();
        knowledgeStarMapService.recalculateLayout(userId);
        return Result.ok();
    }
}
