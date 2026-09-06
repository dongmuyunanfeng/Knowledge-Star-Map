package com.knowledgestarmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.entity.ProjectInfoDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.ProjectExperienceMapper;
import com.knowledgestarmap.mapper.ProjectInfoMapper;
import com.knowledgestarmap.service.ProjectInfoService;
import com.knowledgestarmap.vo.ProjectInfoVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectInfoServiceImpl implements ProjectInfoService {

    private final ProjectInfoMapper projectInfoMapper;
    private final ProjectExperienceMapper projectExperienceMapper;

    public ProjectInfoServiceImpl(ProjectInfoMapper projectInfoMapper,
                                  ProjectExperienceMapper projectExperienceMapper) {
        this.projectInfoMapper = projectInfoMapper;
        this.projectExperienceMapper = projectExperienceMapper;
    }

    @Override
    public ProjectInfoVO createProject(Long userId, String projectName, String projectDesc,
                                        String projectTechStack, String projectRole,
                                        String projectHighlights, String sourceFileIds) {
        ProjectInfoDO project = new ProjectInfoDO();
        project.setUserId(userId);
        project.setProjectName(projectName);
        project.setProjectDesc(projectDesc);
        project.setProjectTechStack(projectTechStack);
        project.setProjectRole(projectRole);
        project.setProjectHighlights(projectHighlights);
        project.setProjectSourceFiles(sourceFileIds);
        project.setIsDeleted(0);
        projectInfoMapper.insert(project);

        return toVO(project);
    }

    @Override
    public ProjectInfoVO getProject(Long projectId, Long userId) {
        ProjectInfoDO project = projectInfoMapper.selectOne(
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .eq(ProjectInfoDO::getId, projectId)
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
        );
        if (project == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "项目不存在");
        }
        return toVO(project);
    }

    @Override
    public PageResult<ProjectInfoVO> listProjects(Long userId, int page, int pageSize) {
        Page<ProjectInfoDO> pageResult = projectInfoMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
                        .orderByDesc(ProjectInfoDO::getCreateTime)
        );

        List<ProjectInfoVO> voList = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), page, pageSize, voList);
    }

    @Override
    public ProjectInfoVO updateProject(Long projectId, Long userId, String projectName,
                                        String projectDesc, String projectTechStack,
                                        String projectRole, String projectHighlights) {
        ProjectInfoDO project = projectInfoMapper.selectOne(
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .eq(ProjectInfoDO::getId, projectId)
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
        );
        if (project == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "项目不存在");
        }

        if (projectName != null) project.setProjectName(projectName);
        if (projectDesc != null) project.setProjectDesc(projectDesc);
        if (projectTechStack != null) project.setProjectTechStack(projectTechStack);
        if (projectRole != null) project.setProjectRole(projectRole);
        if (projectHighlights != null) project.setProjectHighlights(projectHighlights);

        projectInfoMapper.updateById(project);
        return toVO(project);
    }

    @Override
    public void deleteProject(Long projectId, Long userId) {
        ProjectInfoDO project = projectInfoMapper.selectOne(
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .eq(ProjectInfoDO::getId, projectId)
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
        );
        if (project == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "项目不存在");
        }
        projectInfoMapper.deleteById(projectId);
        // 级联物理删除项目经历，避免软删残留占用 uk_user_project 唯一键
        projectExperienceMapper.physicalDeleteByProject(userId, projectId);
    }

    private ProjectInfoVO toVO(ProjectInfoDO project) {
        ProjectInfoVO vo = new ProjectInfoVO();
        vo.setId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setProjectDesc(project.getProjectDesc());
        vo.setProjectTechStack(project.getProjectTechStack());
        vo.setProjectRole(project.getProjectRole());
        vo.setProjectHighlights(project.getProjectHighlights());
        vo.setProjectSourceFiles(project.getProjectSourceFiles());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());
        return vo;
    }
}
