package com.knowledgestarmap.service;

import com.knowledgestarmap.dto.ResumeTemplateSaveDTO;
import com.knowledgestarmap.vo.ResumeMaterialVO;
import com.knowledgestarmap.vo.ResumeTemplateVO;

import java.util.List;

public interface ResumeService {
    ResumeMaterialVO generateResume(Long userId, boolean forceRegenerate, String targetNeed, String requirement);
    ResumeMaterialVO getResume(Long userId);
    List<ResumeTemplateVO> listTemplates(Long userId);
    ResumeTemplateVO saveTemplate(Long userId, ResumeTemplateSaveDTO dto);
    void deleteTemplate(Long userId, Long id);
}
