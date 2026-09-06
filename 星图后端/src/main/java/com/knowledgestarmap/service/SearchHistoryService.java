package com.knowledgestarmap.service;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.vo.SearchHistoryVO;

public interface SearchHistoryService {
    void addHistory(Long userId, String keyword, int resultCount);
    PageResult<SearchHistoryVO> listHistory(Long userId, int page, int pageSize);
    void deleteHistory(Long historyId, Long userId);
}
