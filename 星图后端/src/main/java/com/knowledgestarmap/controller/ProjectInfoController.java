package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.ProjectCreateDTO;
import com.knowledgestarmap.dto.ProjectUpdateDTO;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.KnowledgeQueryService;
import com.knowledgestarmap.service.ProjectInfoService;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import com.knowledgestarmap.vo.ProjectInfoVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectInfoController {

    private final ProjectInfoService projectInfoService;
    private final KnowledgeQueryService knowledgeQueryService;

    public ProjectInfoController(ProjectInfoService projectInfoService,
                                 KnowledgeQueryService knowledgeQueryService) {
        this.projectInfoService = projectInfoService;
        this.knowledgeQueryService = knowledgeQueryService;
    }

    @GetMapping
    public Result<PageResult<ProjectInfoVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.getUserId();
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        return Result.ok(projectInfoService.listProjects(userId, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<ProjectInfoVO> getById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.ok(projectInfoService.getProject(id, userId));
    }

    @GetMapping("/{id}/knowledge")
    public Result<List<KnowledgeInfoVO>> getKnowledge(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        ProjectInfoVO project = projectInfoService.getProject(id, userId);
        List<KnowledgeInfoVO> list = knowledgeQueryService.getByProjectId(id, userId);
        list.forEach(vo -> vo.setProjectName(project.getProjectName()));
        return Result.ok(list);
    }

    @PostMapping
    public Result<ProjectInfoVO> create(@RequestBody ProjectCreateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(projectInfoService.createProject(
                userId,
                dto.getProjectName(),
                dto.getProjectDesc(),
                dto.getProjectTechStack(),
                dto.getProjectRole(),
                dto.getProjectHighlights(),
                dto.getSourceFileIds()
        ));
    }

    @PutMapping("/{id}")
    public Result<ProjectInfoVO> update(@PathVariable Long id, @RequestBody ProjectUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(projectInfoService.updateProject(
                id, userId,
                dto.getProjectName(),
                dto.getProjectDesc(),
                dto.getProjectTechStack(),
                dto.getProjectRole(),
                dto.getProjectHighlights()
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        projectInfoService.deleteProject(id, userId);
        return Result.ok();
    }
}
