package com.knowledgestarmap.service;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.KnowledgeQueryDTO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.vo.KnowledgeInfoVO;

import java.util.List;

public interface KnowledgeQueryService {
    PageResult<KnowledgeInfoVO> list(KnowledgeQueryDTO dto, Long userId);
    PageResult<KnowledgeInfoVO> search(KnowledgeQueryDTO dto, Long userId);
    KnowledgeInfoVO getById(Long knowledgeId, Long userId);
    List<KnowledgeInfoVO> getByFileId(Long fileId, Long userId);
    List<KnowledgeInfoVO> getByProjectId(Long projectId, Long userId);
    KnowledgeInfoVO createKnowledge(KnowledgeInfoDO knowledgeInfo, Long userId);
    void updateKnowledge(com.knowledgestarmap.entity.KnowledgeInfoDO knowledgeInfo, Long userId);
    void deleteKnowledge(Long knowledgeId, Long userId);
    List<String> listDistinctTags(Long userId);
    List<String> listDistinctDomains(Long userId);
}
