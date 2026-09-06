package com.knowledgestarmap.service;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.vo.ProjectInfoVO;

public interface ProjectInfoService {
    ProjectInfoVO createProject(Long userId, String projectName, String projectDesc, String projectTechStack, String projectRole, String projectHighlights, String sourceFileIds);
    ProjectInfoVO getProject(Long projectId, Long userId);
    PageResult<ProjectInfoVO> listProjects(Long userId, int page, int pageSize);
    ProjectInfoVO updateProject(Long projectId, Long userId, String projectName, String projectDesc, String projectTechStack, String projectRole, String projectHighlights);
    void deleteProject(Long projectId, Long userId);
}
