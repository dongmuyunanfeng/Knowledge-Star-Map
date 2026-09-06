package com.knowledgestarmap.service;

import com.knowledgestarmap.vo.KnowledgeSuggestionVO;

import java.util.List;

public interface KnowledgeCompletionService {
    KnowledgeSuggestionVO generateSuggestion(Long knowledgeId, Integer suggestionType, Long userId);
    KnowledgeSuggestionVO getSuggestionById(Long suggestionId, Long userId);
    void approveSuggestion(Long suggestionId, Long userId);
    void rejectSuggestion(Long suggestionId, Long userId);
    List<KnowledgeSuggestionVO> listSuggestions(Long knowledgeId, Long userId, int page, int pageSize, Integer status);
    Boolean getEnableAutoSuggestion(Long userId);
    void setEnableAutoSuggestion(Long userId, Boolean enabled);
}
