package com.knowledgestarmap.service;

import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import com.knowledgestarmap.vo.ProjectStarMapVO;
import com.knowledgestarmap.vo.StarMapStatsVO;

import java.util.List;

public interface KnowledgeStarMapService {
    List<KnowledgeStarMapVO> listDomains(Long userId);
    List<ProjectStarMapVO> listProjects(Long userId);
    StarMapStatsVO getStats(Long userId);
    void recalculateLayout(Long userId);
    void invalidateCache(Long userId);
    void createDomain(Long userId, String domainName);
}
