package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.ProjectExperienceService;
import com.knowledgestarmap.vo.ProjectExperienceVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectExperienceController {

    private final ProjectExperienceService projectExperienceService;

    public ProjectExperienceController(ProjectExperienceService projectExperienceService) {
        this.projectExperienceService = projectExperienceService;
    }

    @GetMapping("/experiences")
    public Result<List<ProjectExperienceVO>> listExperiences() {
        Long userId = UserContext.getUserId();
        return Result.ok(projectExperienceService.listExperiences(userId));
    }

    @PostMapping("/{id}/experience")
    public Result<ProjectExperienceVO> regenerateExperience(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.ok(projectExperienceService.regenerateExperience(id, userId));
    }
}
