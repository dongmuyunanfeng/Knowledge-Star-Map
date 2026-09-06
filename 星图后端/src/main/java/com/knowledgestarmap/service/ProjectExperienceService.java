package com.knowledgestarmap.service;

import com.knowledgestarmap.vo.ProjectExperienceResult;
import com.knowledgestarmap.vo.ProjectExperienceVO;

import java.util.List;

public interface ProjectExperienceService {

    /** 路径B：基于项目内容生成简历级「项目经历」（STAR 叙事），失败返回空结果不抛异常。 */
    ProjectExperienceResult generateExperience(String content, String fileName,
                                               List<String> existingDomains, List<String> existingTags);

    /** 列出当前用户全部项目的项目经历（含未生成/失败的，便于触发重新生成）。 */
    List<ProjectExperienceVO> listExperiences(Long userId);

    /** 重新生成指定项目的项目经历（仅路径B，不重跑知识点解析）。 */
    ProjectExperienceVO regenerateExperience(Long projectId, Long userId);

    /** 落库 upsert：exp 为空时置 generateStatus=2（失败），否则 1（成功）。 */
    void upsertExperience(Long userId, Long projectId, ProjectExperienceResult exp);
}
