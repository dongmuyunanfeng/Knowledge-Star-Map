package com.knowledgestarmap.service;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.KnowledgeQueryParam;
import com.knowledgestarmap.vo.KnowledgeInfoVO;

public interface KnowledgeSearchService {
    PageResult<KnowledgeInfoVO> search(KnowledgeQueryParam dto, Long userId);
}
