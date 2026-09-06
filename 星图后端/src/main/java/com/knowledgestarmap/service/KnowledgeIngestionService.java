package com.knowledgestarmap.service;

import com.knowledgestarmap.agent.KnowledgeIngestionResult;
import com.knowledgestarmap.vo.KnowledgeSliceInfo;

import java.util.List;

public interface KnowledgeIngestionService {
    KnowledgeIngestionResult ingestFile(Long fileId, Long userId, List<KnowledgeSliceInfo> slices);
    void upsertDomain(Long userId, String domainName);
    KnowledgeIngestionResult reparseFile(Long fileId, Long userId, List<KnowledgeSliceInfo> slices);
    int reparseFileFull(Long fileId, Long userId, List<KnowledgeSliceInfo> slices);
}
