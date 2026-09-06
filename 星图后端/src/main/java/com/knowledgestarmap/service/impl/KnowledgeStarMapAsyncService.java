package com.knowledgestarmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgestarmap.entity.FileKnowledgeDO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.KnowledgeStarMapDO;
import com.knowledgestarmap.mapper.FileKnowledgeMapper;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.KnowledgeStarMapMapper;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeStarMapAsyncService {

    @Autowired
    private KnowledgeStarMapMapper knowledgeStarMapMapper;
    @Autowired
    private KnowledgeInfoMapper knowledgeInfoMapper;
    @Autowired
    private FileKnowledgeMapper fileKnowledgeMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Async
    public void doRecalculateAsync(Long userId) {
        try {
            List<KnowledgeInfoDO> allKnowledge = knowledgeInfoMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeInfoDO>()
                            .eq(KnowledgeInfoDO::getUserId, userId)
                            .eq(KnowledgeInfoDO::getIsDeleted, 0)
            );

            Map<String, List<KnowledgeInfoDO>> byDomain = allKnowledge.stream()
                    .filter(k -> k.getKnowledgeDomain() != null && !k.getKnowledgeDomain().isBlank())
                    .collect(Collectors.groupingBy(KnowledgeInfoDO::getKnowledgeDomain));

            List<KnowledgeStarMapDO> existingDomains = knowledgeStarMapMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeStarMapDO>()
                            .eq(KnowledgeStarMapDO::getUserId, userId)
                            .eq(KnowledgeStarMapDO::getIsDeleted, 0)
            );
            Map<String, KnowledgeStarMapDO> domainMap = existingDomains.stream()
                    .collect(Collectors.toMap(KnowledgeStarMapDO::getDomainName, d -> d, (a, b) -> a));

            Map<String, Integer> knowledgeCountMap = new HashMap<>();
            Map<String, BigDecimal> masteryScoreMap = new HashMap<>();
            for (Map.Entry<String, List<KnowledgeInfoDO>> entry : byDomain.entrySet()) {
                knowledgeCountMap.put(entry.getKey(), entry.getValue().size());
                BigDecimal avg = entry.getValue().stream()
                        .map(k -> k.getMasteryScore() != null ? k.getMasteryScore() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(entry.getValue().size()), 2, BigDecimal.ROUND_HALF_UP);
                masteryScoreMap.put(entry.getKey(), avg);
            }

            Map<String, Map<String, Integer>> coOccurrence = new HashMap<>();
            List<FileKnowledgeDO> fileKnowledgeList = fileKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<FileKnowledgeDO>()
                            .eq(FileKnowledgeDO::getUserId, userId)
                            .eq(FileKnowledgeDO::getIsDeleted, 0)
            );

            Map<Long, String> knowledgeIdToDomain = allKnowledge.stream()
                    .filter(k -> k.getKnowledgeDomain() != null)
                    .collect(Collectors.toMap(KnowledgeInfoDO::getId, KnowledgeInfoDO::getKnowledgeDomain, (a, b) -> a));

            Map<Long, List<Long>> fileToKnowledgeIds = fileKnowledgeList.stream()
                    .collect(Collectors.groupingBy(FileKnowledgeDO::getFileId,
                            Collectors.mapping(FileKnowledgeDO::getKnowledgeId, Collectors.toList())));

            for (Map.Entry<Long, List<Long>> fileEntry : fileToKnowledgeIds.entrySet()) {
                List<Long> kIds = fileEntry.getValue();
                Map<String, Integer> tagCountInFile = new HashMap<>();
                for (Long kId : kIds) {
                    String tag = knowledgeIdToDomain.get(kId);
                    if (tag != null) {
                        tagCountInFile.merge(tag, 1, Integer::sum);
                    }
                }
                List<String> tags = new ArrayList<>(tagCountInFile.keySet());
                for (int i = 0; i < tags.size(); i++) {
                    for (int j = i + 1; j < tags.size(); j++) {
                        String t1 = tags.get(i);
                        String t2 = tags.get(j);
                        coOccurrence.computeIfAbsent(t1, k -> new HashMap<>()).merge(t2, 1, Integer::sum);
                        coOccurrence.computeIfAbsent(t2, k -> new HashMap<>()).merge(t1, 1, Integer::sum);
                    }
                }
            }

            List<String> domainNames = new ArrayList<>(knowledgeCountMap.keySet());
            if (domainNames.isEmpty()) return;

            Map<String, double[]> positions;
            if (domainNames.size() <= 1) {
                positions = calculateRingLayout(domainNames);
            } else {
                positions = calculateForceDirectedLayout(domainNames, knowledgeCountMap, masteryScoreMap, coOccurrence);
            }

            for (String domainName : domainNames) {
                double[] pos = positions.get(domainName);
                KnowledgeStarMapDO domain = domainMap.get(domainName);
                int knowledgeCount = knowledgeCountMap.get(domainName);
                BigDecimal masteryScore = masteryScoreMap.get(domainName);
                double weightFactor = 1.0 + knowledgeCount * 0.05 + masteryScore.doubleValue() * 0.01;
                weightFactor = Math.min(weightFactor, 5.0);

                if (domain != null) {
                    domain.setXCoordinate(BigDecimal.valueOf(pos[0]));
                    domain.setYCoordinate(BigDecimal.valueOf(pos[1]));
                    domain.setKnowledgeCount(knowledgeCount);
                    domain.setMasteryScore(masteryScore);
                    domain.setWeakFlag(masteryScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);
                    domain.setWeightFactor(BigDecimal.valueOf(weightFactor));
                    knowledgeStarMapMapper.updateById(domain);
                } else {
                    KnowledgeStarMapDO newDomain = new KnowledgeStarMapDO();
                    newDomain.setUserId(userId);
                    newDomain.setDomainName(domainName);
                    newDomain.setKnowledgeCount(knowledgeCount);
                    newDomain.setMasteryScore(masteryScore);
                    newDomain.setWeakFlag(masteryScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);
                    newDomain.setXCoordinate(BigDecimal.valueOf(pos[0]));
                    newDomain.setYCoordinate(BigDecimal.valueOf(pos[1]));
                    newDomain.setWeightFactor(BigDecimal.valueOf(weightFactor));
                    newDomain.setIsDeleted(0);
                    knowledgeStarMapMapper.insert(newDomain);
                }
            }

            // 清理已无知识点关联的空领域节点（如重解析/删除知识点后遗留的领域）
            for (KnowledgeStarMapDO existing : existingDomains) {
                if (!byDomain.containsKey(existing.getDomainName())) {
                    knowledgeStarMapMapper.deleteById(existing.getId());
                    log.info("已清理空领域节点: userId={}, domain={}", userId, existing.getDomainName());
                }
            }

            writeCache(userId);
            log.info("星图布局重新计算完成: userId={}, domains={}", userId, domainNames.size());
        } catch (Exception e) {
            log.error("星图布局重新计算失败: userId={}", userId, e);
        }
    }

    private void writeCache(Long userId) {
        try {
            List<KnowledgeStarMapDO> domains = knowledgeStarMapMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeStarMapDO>()
                            .eq(KnowledgeStarMapDO::getUserId, userId)
                            .eq(KnowledgeStarMapDO::getIsDeleted, 0)
            );
            List<KnowledgeStarMapVO> voList = domains.stream().map(d -> {
                KnowledgeStarMapVO vo = new KnowledgeStarMapVO();
                vo.setId(d.getId());
                vo.setDomainName(d.getDomainName());
                vo.setKnowledgeCount(d.getKnowledgeCount());
                vo.setMasteryScore(d.getMasteryScore());
                vo.setWeakFlag(d.getWeakFlag());
                vo.setXCoordinate(d.getXCoordinate());
                vo.setYCoordinate(d.getYCoordinate());
                vo.setWeightFactor(d.getWeightFactor());
                return vo;
            }).collect(Collectors.toList());
            String key = com.knowledgestarmap.util.RedisKeyBuilder.starMap(userId);
            redisTemplate.opsForValue().set(key, voList);
            log.info("星图缓存已写入: userId={}", userId);
        } catch (Exception e) {
            log.warn("星图缓存写入失败: userId={}", userId, e);
        }
    }

    private Map<String, double[]> calculateRingLayout(List<String> domainNames) {
        Map<String, double[]> positions = new HashMap<>();
        if (domainNames.isEmpty()) return positions;
        if (domainNames.size() == 1) {
            positions.put(domainNames.get(0), new double[]{400, 300});
            return positions;
        }
        double radius = 200;
        double centerX = 400;
        double centerY = 300;
        for (int i = 0; i < domainNames.size(); i++) {
            double angle = 2 * Math.PI * i / domainNames.size();
            positions.put(domainNames.get(i), new double[]{
                    centerX + radius * Math.cos(angle),
                    centerY + radius * Math.sin(angle)
            });
        }
        return positions;
    }

    private Map<String, double[]> calculateForceDirectedLayout(
            List<String> domainNames,
            Map<String, Integer> knowledgeCountMap,
            Map<String, BigDecimal> masteryScoreMap,
            Map<String, Map<String, Integer>> coOccurrence) {
        Map<String, double[]> positions = new HashMap<>();
        int n = domainNames.size();
        double centerX = 400;
        double centerY = 300;

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            double radius = 150 + Math.random() * 100;
            positions.put(domainNames.get(i), new double[]{
                    centerX + radius * Math.cos(angle),
                    centerY + radius * Math.sin(angle)
            });
        }

        double[][] edgeWeights = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String t1 = domainNames.get(i);
                String t2 = domainNames.get(j);
                int coCount = coOccurrence.getOrDefault(t1, new HashMap<>()).getOrDefault(t2, 0);
                int maxCount = Math.max(knowledgeCountMap.getOrDefault(t1, 1), knowledgeCountMap.getOrDefault(t2, 1));
                double strength = (double) coCount / maxCount;
                if (strength >= 0.1) {
                    edgeWeights[i][j] = strength;
                    edgeWeights[j][i] = strength;
                }
            }
        }

        for (int iter = 0; iter < 50; iter++) {
            double[] forces = new double[n * 2];

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = positions.get(domainNames.get(j))[0] - positions.get(domainNames.get(i))[0];
                    double dy = positions.get(domainNames.get(j))[1] - positions.get(domainNames.get(i))[1];
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 1) dist = 1;

                    double baseStrength = knowledgeCountMap.getOrDefault(domainNames.get(i), 1)
                            * knowledgeCountMap.getOrDefault(domainNames.get(j), 1);
                    double force = 5000.0 * baseStrength / (dist * dist);

                    if (edgeWeights[i][j] >= 0.1) {
                        force *= edgeWeights[i][j];
                    }

                    double fx = (dx / dist) * force;
                    double fy = (dy / dist) * force;

                    forces[i * 2] += fx;
                    forces[i * 2 + 1] += fy;
                    forces[j * 2] -= fx;
                    forces[j * 2 + 1] -= fy;
                }
            }

            for (int i = 0; i < n; i++) {
                double dx = centerX - positions.get(domainNames.get(i))[0];
                double dy = centerY - positions.get(domainNames.get(i))[1];
                forces[i * 2] += dx * 0.01;
                forces[i * 2 + 1] += dy * 0.01;
            }

            double cooling = 1.0 - iter / 50.0;
            for (int i = 0; i < n; i++) {
                double x = positions.get(domainNames.get(i))[0] + forces[i * 2] * cooling;
                double y = positions.get(domainNames.get(i))[1] + forces[i * 2 + 1] * cooling;
                x = Math.max(50, Math.min(750, x));
                y = Math.max(50, Math.min(550, y));
                positions.put(domainNames.get(i), new double[]{x, y});
            }
        }

        return positions;
    }
}
