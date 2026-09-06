package com.knowledgestarmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgestarmap.entity.FileKnowledgeDO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.KnowledgeStarMapDO;
import com.knowledgestarmap.entity.ProjectInfoDO;
import com.knowledgestarmap.mapper.FileKnowledgeMapper;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.KnowledgeStarMapMapper;
import com.knowledgestarmap.mapper.ProjectInfoMapper;
import com.knowledgestarmap.service.KnowledgeStarMapService;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.KnowledgeStarMapVO;
import com.knowledgestarmap.vo.ProjectStarMapVO;
import com.knowledgestarmap.vo.StarMapStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeStarMapServiceImpl implements KnowledgeStarMapService {

    private final KnowledgeStarMapMapper knowledgeStarMapMapper;
    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final FileKnowledgeMapper fileKnowledgeMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KnowledgeStarMapAsyncService asyncService;
    private final int invalidateLockTtlSeconds;
    private final int recalculateLockTtlSeconds;

    public KnowledgeStarMapServiceImpl(KnowledgeStarMapMapper knowledgeStarMapMapper,
                                       KnowledgeInfoMapper knowledgeInfoMapper,
                                       FileKnowledgeMapper fileKnowledgeMapper,
                                       ProjectInfoMapper projectInfoMapper,
                                       StringRedisTemplate stringRedisTemplate,
                                       RedisTemplate<String, Object> redisTemplate,
                                       KnowledgeStarMapAsyncService asyncService,
                                       @Value("${app.star-map.invalidate-lock-ttl-seconds:2}") int invalidateLockTtlSeconds,
                                       @Value("${app.star-map.recalculate-lock-ttl-seconds:30}") int recalculateLockTtlSeconds) {
        this.knowledgeStarMapMapper = knowledgeStarMapMapper;
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.fileKnowledgeMapper = fileKnowledgeMapper;
        this.projectInfoMapper = projectInfoMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.asyncService = asyncService;
        this.invalidateLockTtlSeconds = invalidateLockTtlSeconds;
        this.recalculateLockTtlSeconds = recalculateLockTtlSeconds;
    }

    @Override
    public List<KnowledgeStarMapVO> listDomains(Long userId) {
        List<KnowledgeStarMapDO> domains = knowledgeStarMapMapper.selectList(
                new LambdaQueryWrapper<KnowledgeStarMapDO>()
                        .eq(KnowledgeStarMapDO::getUserId, userId)
                        .eq(KnowledgeStarMapDO::getIsDeleted, 0)
        );
        return domains.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<ProjectStarMapVO> listProjects(Long userId) {
        List<ProjectInfoDO> projects = projectInfoMapper.selectList(
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
                        .orderByDesc(ProjectInfoDO::getCreateTime)
        );

        List<KnowledgeInfoDO> projectKnowledge = knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
                        .isNotNull(KnowledgeInfoDO::getProjectId)
        );

        Map<Long, List<KnowledgeInfoDO>> byProject = projectKnowledge.stream()
                .filter(k -> k.getProjectId() != null)
                .collect(Collectors.groupingBy(KnowledgeInfoDO::getProjectId));

        return projects.stream().map(p -> {
            ProjectStarMapVO vo = new ProjectStarMapVO();
            vo.setId(p.getId());
            vo.setProjectName(p.getProjectName());
            List<KnowledgeInfoDO> items = byProject.getOrDefault(p.getId(), Collections.emptyList());
            vo.setKnowledgeCount(items.size());
            BigDecimal avgScore = BigDecimal.ZERO;
            if (!items.isEmpty()) {
                avgScore = items.stream()
                        .map(k -> k.getMasteryScore() != null ? k.getMasteryScore() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(items.size()), 2, BigDecimal.ROUND_HALF_UP);
            }
            vo.setMasteryScore(avgScore);
            vo.setWeakFlag(avgScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);
            vo.setXCoordinate(null);
            vo.setYCoordinate(null);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public StarMapStatsVO getStats(Long userId) {
        List<KnowledgeInfoDO> allKnowledge = knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
        );

        StarMapStatsVO stats = new StarMapStatsVO();
        stats.setTotalKnowledgeCount(allKnowledge.size());

        if (allKnowledge.isEmpty()) {
            stats.setAverageMasteryScore(BigDecimal.ZERO);
            stats.setDomains(Collections.emptyList());
            return stats;
        }

        BigDecimal totalScore = allKnowledge.stream()
                .map(k -> k.getMasteryScore() != null ? k.getMasteryScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setAverageMasteryScore(totalScore.divide(
                BigDecimal.valueOf(allKnowledge.size()), 2, BigDecimal.ROUND_HALF_UP));

        Map<String, List<KnowledgeInfoDO>> byDomain = allKnowledge.stream()
                .filter(k -> k.getKnowledgeDomain() != null && !k.getKnowledgeDomain().isBlank())
                .collect(Collectors.groupingBy(KnowledgeInfoDO::getKnowledgeDomain));

        List<KnowledgeStarMapVO> domainItems = new ArrayList<>();
        for (Map.Entry<String, List<KnowledgeInfoDO>> entry : byDomain.entrySet()) {
            List<KnowledgeInfoDO> items = entry.getValue();
            BigDecimal avgScore = items.stream()
                    .map(k -> k.getMasteryScore() != null ? k.getMasteryScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(items.size()), 2, BigDecimal.ROUND_HALF_UP);
            KnowledgeStarMapVO vo = new KnowledgeStarMapVO();
            vo.setDomainName(entry.getKey());
            vo.setKnowledgeCount(items.size());
            vo.setMasteryScore(avgScore);
            vo.setWeakFlag(avgScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);
            domainItems.add(vo);
        }
        stats.setDomains(domainItems);
        return stats;
    }

    @Override
    public void recalculateLayout(Long userId) {
        String lockKey = RedisKeyBuilder.layoutLock(userId);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(recalculateLockTtlSeconds));
        if (Boolean.TRUE.equals(acquired)) {
            log.info("星图布局锁获取成功: userId={}", userId);
            asyncService.doRecalculateAsync(userId);
        } else {
            log.info("星图布局已有并发计算中，跳过: userId={}", userId);
        }
    }

    @Override
    public void invalidateCache(Long userId) {
        try {
            String key = RedisKeyBuilder.starMapInvalidateLock(userId);
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "invalid", java.time.Duration.ofSeconds(invalidateLockTtlSeconds));
            if (Boolean.TRUE.equals(acquired)) {
                log.info("星图缓存失效锁获取成功: userId={}", userId);
                asyncService.doRecalculateAsync(userId);
            } else {
                log.info("星图缓存已有并发处理中，跳过: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("星图缓存失效失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void createDomain(Long userId, String domainName) {
        if (domainName == null || domainName.isBlank()) {
            return;
        }

        KnowledgeStarMapDO existing = knowledgeStarMapMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeStarMapDO>()
                        .eq(KnowledgeStarMapDO::getUserId, userId)
                        .eq(KnowledgeStarMapDO::getDomainName, domainName)
                        .eq(KnowledgeStarMapDO::getIsDeleted, 0)
        );

        if (existing != null) {
            long knowledgeCount = knowledgeInfoMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeInfoDO>()
                            .eq(KnowledgeInfoDO::getUserId, userId)
                            .eq(KnowledgeInfoDO::getKnowledgeDomain, domainName)
                            .eq(KnowledgeInfoDO::getIsDeleted, 0)
            );

            List<KnowledgeInfoDO> knowledgeList = knowledgeInfoMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeInfoDO>()
                            .eq(KnowledgeInfoDO::getUserId, userId)
                            .eq(KnowledgeInfoDO::getKnowledgeDomain, domainName)
                            .eq(KnowledgeInfoDO::getIsDeleted, 0)
            );

            BigDecimal avgScore = BigDecimal.ZERO;
            if (!knowledgeList.isEmpty()) {
                avgScore = knowledgeList.stream()
                        .map(KnowledgeInfoDO::getMasteryScore)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(knowledgeList.size()), 2, BigDecimal.ROUND_HALF_UP);
            }

            double weightFactor = 1.0 + knowledgeCount * 0.05 + avgScore.doubleValue() * 0.01;
            existing.setKnowledgeCount((int) knowledgeCount);
            existing.setMasteryScore(avgScore);
            existing.setWeakFlag(avgScore.compareTo(new BigDecimal("70")) < 0 ? 1 : 0);
            existing.setWeightFactor(BigDecimal.valueOf(Math.min(weightFactor, 5.0)));

            knowledgeStarMapMapper.updateById(existing);
        } else {
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
        }

        invalidateCache(userId);
    }

    void writeCache(Long userId) {
        try {
            List<KnowledgeStarMapVO> domains = listDomains(userId);
            String key = RedisKeyBuilder.starMap(userId);
            redisTemplate.opsForValue().set(key, domains);
            log.info("星图缓存已写入: userId={}", userId);
        } catch (Exception e) {
            log.warn("星图缓存写入失败: userId={}", userId, e);
        }
    }

    private KnowledgeStarMapVO toVO(KnowledgeStarMapDO domain) {
        KnowledgeStarMapVO vo = new KnowledgeStarMapVO();
        vo.setId(domain.getId());
        vo.setDomainName(domain.getDomainName());
        vo.setKnowledgeCount(domain.getKnowledgeCount());
        vo.setMasteryScore(domain.getMasteryScore());
        vo.setWeakFlag(domain.getWeakFlag());
        vo.setXCoordinate(domain.getXCoordinate());
        vo.setYCoordinate(domain.getYCoordinate());
        vo.setWeightFactor(domain.getWeightFactor());
        return vo;
    }
}
