package com.knowledgestarmap.agent;

import com.knowledgestarmap.entity.KnowledgeInfoDO;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeIngestionResult {
    private int newKnowledgeCount;
    private int duplicateCount;
    private List<String> newDomains;
    private List<KnowledgeInfoDO> createdKnowledge;
    private List<String> duplicateHashes = new ArrayList<>();
}
