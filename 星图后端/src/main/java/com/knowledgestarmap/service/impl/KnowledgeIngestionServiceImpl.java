package com.knowledgestarmap.service.impl;

import com.knowledgestarmap.agent.KnowledgeIngestionResult;
import com.knowledgestarmap.entity.*;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.*;
import com.knowledgestarmap.service.KnowledgeIngestionService;
import com.knowledgestarmap.util.KnowledgeHashUtil;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.KnowledgeSliceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private static final int MAX_KNOWLEDGE_CONTENT_LENGTH = 10000;
    private static final int MAX_COMPLETE_CONTENT_LENGTH = 20000;

    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final FileKnowledgeMapper fileKnowledgeMapper;
    private final KnowledgeStarMapMapper knowledgeStarMapMapper;
    private final KnowledgeSuggestionMapper knowledgeSuggestionMapper;
    private final KnowledgeCompleteLogMapper knowledgeCompleteLogMapper;
    private final com.knowledgestarmap.service.KnowledgeStarMapService knowledgeStarMapService;
    private final RedisTemplate<String, Object> redisTemplate;

    public KnowledgeIngestionServiceImpl(KnowledgeInfoMapper knowledgeInfoMapper,
                                         FileKnowledgeMapper fileKnowledgeMapper,
                                         KnowledgeStarMapMapper knowledgeStarMapMapper,
                                         KnowledgeSuggestionMapper knowledgeSuggestionMapper,
                                         KnowledgeCompleteLogMapper knowledgeCompleteLogMapper,
                                         com.knowledgestarmap.service.KnowledgeStarMapService knowledgeStarMapService,
                                         RedisTemplate<String, Object> redisTemplate) {
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.fileKnowledgeMapper = fileKnowledgeMapper;
        this.knowledgeStarMapMapper = knowledgeStarMapMapper;
        this.knowledgeSuggestionMapper = knowledgeSuggestionMapper;
        this.knowledgeCompleteLogMapper = knowledgeCompleteLogMapper;
        this.knowledgeStarMapService = knowledgeStarMapService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIngestionResult ingestFile(Long fileId, Long userId, List<KnowledgeSliceInfo> slices) {
        KnowledgeIngestionResult result = new KnowledgeIngestionResult();
        result.setCreatedKnowledge(new ArrayList<>());
        result.setNewDomains(new ArrayList<>());
        if (slices == null || slices.isEmpty()) {
            return result;
        }

        List<KnowledgeInfoDO> createdKnowledge = new ArrayList<>();
        Set<String> newDomains = new HashSet<>();
        int duplicateCount = 0;

        List<String> existingHashes = knowledgeInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeInfoDO>()
                        .select("knowledge_hash")
                        .eq("user_id", userId)
                        .eq("is_deleted", 0)
        ).stream().map(KnowledgeInfoDO::getKnowledgeHash).collect(Collectors.toList());

        for (KnowledgeSliceInfo slice : slices) {
            if (slice.getContent() == null || slice.getContent().length() > MAX_KNOWLEDGE_CONTENT_LENGTH) {
                log.warn("知识点内容超限，跳过: name={}", slice.getName());
                continue;
            }

            String hash = KnowledgeHashUtil.computeHash(slice.getName(), slice.getContent());

            if (existingHashes.contains(hash)) {
                duplicateCount++;
                result.getDuplicateHashes().add(hash);
                log.info("知识点重复（MD5去重）: name={}", slice.getName());
                linkDuplicateToFile(fileId, userId, hash, slice);
                continue;
            }

            try {
                if (isSemanticallyDuplicate(userId, slice.getName(), slice.getContent())) {
                    duplicateCount++;
                    result.getDuplicateHashes().add(hash);
                    log.info("知识点重复（语义去重）: name={}", slice.getName());
                    continue;
                }
            } catch (Exception e) {
                log.warn("语义相似度服务异常，降级为仅MD5去重: {}", e.getMessage());
            }

            KnowledgeInfoDO knowledgeInfo = new KnowledgeInfoDO();
            knowledgeInfo.setUserId(userId);
            knowledgeInfo.setKnowledgeName(slice.getName());
            knowledgeInfo.setKnowledgeDomain(slice.getDomain() != null && !slice.getDomain().isBlank() ? slice.getDomain() : "其他");
            knowledgeInfo.setKnowledgeTag(slice.getTag());
            knowledgeInfo.setProjectId(slice.getProjectId());
            knowledgeInfo.setKnowledgeContent(slice.getContent().substring(0, Math.min(slice.getContent().length(), MAX_KNOWLEDGE_CONTENT_LENGTH)));
            knowledgeInfo.setMasteryLevel(1);
            knowledgeInfo.setMasteryScore(new BigDecimal("50.00"));
            knowledgeInfo.setIsCompleted(0);
            knowledgeInfo.setKnowledgeHash(hash);
            knowledgeInfo.setSourceType(1);
            knowledgeInfo.setIsDeleted(0);
            knowledgeInfoMapper.insert(knowledgeInfo);

            createdKnowledge.add(knowledgeInfo);

            // 文件-知识点关联：content_segment 存简短位置描述（如"第3行-第20行"），用于溯源定位
            FileKnowledgeDO fileKnowledge = new FileKnowledgeDO();
            fileKnowledge.setUserId(userId);
            fileKnowledge.setFileId(fileId);
            fileKnowledge.setKnowledgeId(knowledgeInfo.getId());
            String location = slice.getSourceLocation();
            if (location == null || location.isBlank()) {
                location = slice.getContent() != null && slice.getContent().length() > 30
                        ? slice.getContent().substring(0, 30) + "..."
                        : slice.getContent();
            }
            fileKnowledge.setContentSegment(location);
            fileKnowledge.setSegmentStart(slice.getStartPosition());
            fileKnowledge.setSegmentEnd(Math.max(slice.getStartPosition(), slice.getEndPosition()));
            fileKnowledge.setIsDeleted(0);
            fileKnowledgeMapper.insert(fileKnowledge);

            if (slice.getDomain() != null && !slice.getDomain().isBlank()) {
                upsertDomain(userId, slice.getDomain());
                newDomains.add(slice.getDomain());
            }
        }

        result.setNewKnowledgeCount(createdKnowledge.size());
        result.setDuplicateCount(duplicateCount);
        result.setNewDomains(new ArrayList<>(newDomains));
        result.setCreatedKnowledge(createdKnowledge);
        result.setDuplicateHashes(new ArrayList<>(result.getDuplicateHashes()));

        invalidateStarMapCache(userId);
        return result;
    }

    @Override
    public void upsertDomain(Long userId, String domainName) {
        if (domainName == null || domainName.isBlank()) return;

        KnowledgeStarMapDO existing = knowledgeStarMapMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeStarMapDO>()
                        .eq("user_id", userId)
                        .eq("domain_name", domainName)
                        .eq("is_deleted", 0)
        );

        if (existing == null) {
            KnowledgeStarMapDO domain = new KnowledgeStarMapDO();
            domain.setUserId(userId);
            domain.setDomainName(domainName);
            domain.setKnowledgeCount(0);
            domain.setMasteryScore(BigDecimal.ZERO);
            domain.setWeakFlag(1);
            domain.setXCoordinate(null);
            domain.setYCoordinate(null);
            domain.setWeightFactor(BigDecimal.ONE);
            domain.setIsDeleted(0);
            knowledgeStarMapMapper.insert(domain);
            existing = domain;
        }

        // 按领域字段统计该领域下知识点数量与平均掌握分
        List<KnowledgeInfoDO> knowledgeList = knowledgeInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeInfoDO>()
                        .eq("user_id", userId)
                        .eq("knowledge_domain", domainName)
                        .eq("is_deleted", 0)
        );

        int knowledgeCount = knowledgeList.size();
        BigDecimal avgScore = BigDecimal.ZERO;
        if (!knowledgeList.isEmpty()) {
            avgScore = knowledgeList.stream()
                    .map(KnowledgeInfoDO::getMasteryScore)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(knowledgeList.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        existing.setKnowledgeCount(knowledgeCount);
        existing.setMasteryScore(avgScore);
        existing.setWeakFlag(avgScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);

        double weightFactor = 1.0 + knowledgeCount * 0.05 + avgScore.doubleValue() * 0.01;
        existing.setWeightFactor(BigDecimal.valueOf(Math.min(weightFactor, 5.0)));

        knowledgeStarMapMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reparseFileFull(Long fileId, Long userId, List<KnowledgeSliceInfo> slices) {
        return doReparse(fileId, userId, slices).getNewKnowledgeCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIngestionResult reparseFile(Long fileId, Long userId, List<KnowledgeSliceInfo> slices) {
        return doReparse(fileId, userId, slices);
    }

    private KnowledgeIngestionResult doReparse(Long fileId, Long userId, List<KnowledgeSliceInfo> slices) {
        // 1. 收集该文件关联的知识点ID
        Set<Long> knowledgeIds = fileKnowledgeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileKnowledgeDO>()
                        .eq("file_id", fileId)
                        .eq("user_id", userId)
        ).stream().map(FileKnowledgeDO::getKnowledgeId).collect(Collectors.toSet());

        // 2. 物理删除该文件全部关联记录（uk_file_knowledge 不含 is_deleted，逻辑删除行会占用唯一键导致重新入库冲突）
        fileKnowledgeMapper.physicalDeleteByFileId(fileId, userId);

        // 3. 删除不再被其他文件引用的知识点及其派生数据（物理删除，避免同hash逻辑删除行触发唯一键冲突）
        for (Long knowledgeId : knowledgeIds) {
            Long remainingCount = fileKnowledgeMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileKnowledgeDO>()
                            .eq("knowledge_id", knowledgeId)
                            .eq("user_id", userId)
            );
            if (remainingCount == 0) {
                knowledgeSuggestionMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeSuggestionDO>()
                                .eq("knowledge_id", knowledgeId)
                                .eq("user_id", userId)
                );
                knowledgeCompleteLogMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeCompleteLogDO>()
                                .eq("knowledge_id", knowledgeId)
                                .eq("user_id", userId)
                );
                knowledgeInfoMapper.physicalDeleteById(knowledgeId);
            }
        }

        // 4. 重新入库
        return ingestFile(fileId, userId, slices);
    }

    private void linkDuplicateToFile(Long fileId, Long userId, String hash, KnowledgeSliceInfo slice) {
        try {
            KnowledgeInfoDO existing = knowledgeInfoMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeInfoDO>()
                            .eq("user_id", userId)
                            .eq("knowledge_hash", hash)
                            .eq("is_deleted", 0)
                            .last("LIMIT 1")
            );
            if (existing == null) return;

            Long linkCount = fileKnowledgeMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileKnowledgeDO>()
                            .eq("file_id", fileId)
                            .eq("knowledge_id", existing.getId())
                            .eq("user_id", userId)
            );
            if (linkCount > 0) return;

            FileKnowledgeDO fileKnowledge = new FileKnowledgeDO();
            fileKnowledge.setUserId(userId);
            fileKnowledge.setFileId(fileId);
            fileKnowledge.setKnowledgeId(existing.getId());
            String location = slice.getSourceLocation();
            fileKnowledge.setContentSegment(location != null && !location.isBlank() ? location
                    : (slice.getContent() != null && slice.getContent().length() > 30
                        ? slice.getContent().substring(0, 30) + "..." : slice.getContent()));
            fileKnowledge.setSegmentStart(slice.getStartPosition());
            fileKnowledge.setSegmentEnd(Math.max(slice.getStartPosition(), slice.getEndPosition()));
            fileKnowledge.setIsDeleted(0);
            fileKnowledgeMapper.insert(fileKnowledge);
            log.info("重复知识点已建立文件关联: knowledgeId={}, fileId={}", existing.getId(), fileId);
        } catch (Exception e) {
            log.warn("重复知识点建立文件关联失败: {}", e.getMessage());
        }
    }

    private boolean isSemanticallyDuplicate(Long userId, String name, String content) {
        List<KnowledgeInfoDO> candidates = knowledgeInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeInfoDO>()
                        .eq("user_id", userId)
                        .eq("is_deleted", 0)
        );

        for (KnowledgeInfoDO existing : candidates) {
            if (existing.getKnowledgeContent() == null) continue;
            int lengthDiff = Math.abs(existing.getKnowledgeContent().length() - content.length());
            int avgLength = (existing.getKnowledgeContent().length() + content.length()) / 2;
            if (avgLength == 0 || lengthDiff > avgLength * 0.2) continue;

            double similarity = computeCharLevelSimilarity(existing.getKnowledgeContent(), content);
            if (similarity > 0.85) return true;
        }
        return false;
    }

    private double computeCharLevelSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        Set<Character> set1 = new HashSet<>();
        Set<Character> set2 = new HashSet<>();
        for (char c : s1.toCharArray()) set1.add(c);
        for (char c : s2.toCharArray()) set2.add(c);

        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private void invalidateStarMapCache(Long userId) {
        try {
            // 触发星图异步重算（含领域统计刷新与空领域清理）
            knowledgeStarMapService.invalidateCache(userId);
            // 检索结果按查询参数缓存，入库后需按用户前缀整体失效，避免新知识点1小时内不可见
            java.util.Set<String> searchKeys = redisTemplate.keys("search:result:" + userId + ":*");
            if (searchKeys != null && !searchKeys.isEmpty()) {
                redisTemplate.delete(searchKeys);
            }
            log.info("星图/检索缓存已失效: userId={}, searchKeys={}", userId, searchKeys == null ? 0 : searchKeys.size());
        } catch (Exception e) {
            log.warn("星图缓存失效失败: {}", e.getMessage());
        }
    }
}
