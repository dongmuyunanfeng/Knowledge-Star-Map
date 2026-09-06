package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.KnowledgeQueryParam;
import com.knowledgestarmap.entity.FileKnowledgeDO;
import com.knowledgestarmap.entity.FileResourceDO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.ProjectInfoDO;
import com.knowledgestarmap.mapper.FileKnowledgeMapper;
import com.knowledgestarmap.mapper.FileResourceMapper;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.ProjectInfoMapper;
import com.knowledgestarmap.service.KnowledgeSearchService;
import com.knowledgestarmap.vo.FileKnowledgeVO;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private static final long SEARCH_CACHE_TTL_HOURS = 1;

    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final FileKnowledgeMapper fileKnowledgeMapper;
    private final FileResourceMapper fileResourceMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final StringRedisTemplate redisTemplate;

    public KnowledgeSearchServiceImpl(KnowledgeInfoMapper knowledgeInfoMapper,
                                      FileKnowledgeMapper fileKnowledgeMapper,
                                      FileResourceMapper fileResourceMapper,
                                      ProjectInfoMapper projectInfoMapper,
                                      StringRedisTemplate redisTemplate) {
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.fileKnowledgeMapper = fileKnowledgeMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.projectInfoMapper = projectInfoMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PageResult<KnowledgeInfoVO> search(KnowledgeQueryParam dto, Long userId) {
        int page = Math.max(1, dto.getPage() != null ? dto.getPage() : 1);
        int pageSize = Math.min(100, Math.max(1, dto.getPageSize() != null ? dto.getPageSize() : 20));

        String cacheKey = buildCacheKey(userId, dto, page, pageSize);
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                return JSON.parseObject(cachedJson,
                        new com.alibaba.fastjson2.TypeReference<PageResult<KnowledgeInfoVO>>() {});
            } catch (Exception e) {
                log.warn("检索缓存反序列化失败, key={}", cacheKey, e);
            }
        }

        LambdaQueryWrapper<KnowledgeInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeInfoDO::getUserId, userId);
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(KnowledgeInfoDO::getKnowledgeName, dto.getKeyword())
                    .or().like(KnowledgeInfoDO::getKnowledgeContent, dto.getKeyword())
                    .or().like(KnowledgeInfoDO::getKnowledgeTag, dto.getKeyword()));
        }
        if (dto.getTag() != null && !dto.getTag().isBlank()) {
            wrapper.eq(KnowledgeInfoDO::getKnowledgeTag, dto.getTag());
        }
        if (dto.getDomain() != null && !dto.getDomain().isBlank()) {
            wrapper.eq(KnowledgeInfoDO::getKnowledgeDomain, dto.getDomain());
        }
        if (dto.getProjectId() != null) {
            wrapper.eq(KnowledgeInfoDO::getProjectId, dto.getProjectId());
        }
        if (dto.getMasteryLevel() != null) {
            wrapper.eq(KnowledgeInfoDO::getMasteryLevel, dto.getMasteryLevel());
        }
        wrapper.orderByDesc(KnowledgeInfoDO::getCreateTime);

        Page<KnowledgeInfoDO> pageResult = knowledgeInfoMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<KnowledgeInfoVO> voList = pageResult.getRecords().stream().map(k -> {
            KnowledgeInfoVO vo = toSearchVO(k);
            List<FileKnowledgeDO> fileKnowledges = fileKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<FileKnowledgeDO>()
                            .eq(FileKnowledgeDO::getKnowledgeId, k.getId())
                            .eq(FileKnowledgeDO::getUserId, userId)
            );
            if (!fileKnowledges.isEmpty()) {
                List<Long> fileIds = fileKnowledges.stream().map(FileKnowledgeDO::getFileId).distinct().collect(Collectors.toList());
                Map<Long, FileResourceDO> fileMap = fileResourceMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileResourceDO>()
                                .in(FileResourceDO::getId, fileIds)
                                .eq(FileResourceDO::getUserId, userId)
                ).stream().collect(Collectors.toMap(FileResourceDO::getId, f -> f, (a, b) -> a));

                List<FileKnowledgeVO> sources = fileKnowledges.stream().map(fk -> {
                    FileKnowledgeVO fkvo = new FileKnowledgeVO();
                    fkvo.setFileId(fk.getFileId());
                    fkvo.setContentSegment(fk.getContentSegment());
                    fkvo.setSegmentStart(fk.getSegmentStart());
                    fkvo.setSegmentEnd(fk.getSegmentEnd());
                    FileResourceDO fileResource = fileMap.get(fk.getFileId());
                    if (fileResource != null) {
                        fkvo.setFileName(fileResource.getFileName());
                        fkvo.setFilePath(fileResource.getFilePath());
                    }
                    return fkvo;
                }).collect(Collectors.toList());
                vo.setFileSources(sources);
                // use the first file knowledge's segment as the contentSegment for this knowledge
                vo.setContentSegment(fileKnowledges.get(0).getContentSegment());
            }
            return vo;
        }).collect(Collectors.toList());

        fillProjectNames(voList, userId);

        PageResult<KnowledgeInfoVO> result = PageResult.of(pageResult.getTotal(), page, pageSize, voList);

        try {
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), SEARCH_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("检索缓存写入失败, key={}", cacheKey, e);
        }

        return result;
    }

    private void fillProjectNames(List<KnowledgeInfoVO> voList, Long userId) {
        List<Long> projectIds = voList.stream()
                .map(KnowledgeInfoVO::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (projectIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = projectInfoMapper.selectList(
                new LambdaQueryWrapper<ProjectInfoDO>()
                        .in(ProjectInfoDO::getId, projectIds)
                        .eq(ProjectInfoDO::getUserId, userId)
                        .eq(ProjectInfoDO::getIsDeleted, 0)
        ).stream().collect(Collectors.toMap(ProjectInfoDO::getId, ProjectInfoDO::getProjectName, (a, b) -> a));
        voList.forEach(vo -> {
            if (vo.getProjectId() != null) {
                vo.setProjectName(nameMap.get(vo.getProjectId()));
            }
        });
    }

    private KnowledgeInfoVO toSearchVO(KnowledgeInfoDO knowledge) {
        KnowledgeInfoVO vo = new KnowledgeInfoVO();
        vo.setId(knowledge.getId());
        vo.setKnowledgeName(knowledge.getKnowledgeName());
        vo.setKnowledgeDomain(knowledge.getKnowledgeDomain());
        vo.setKnowledgeTag(knowledge.getKnowledgeTag());
        vo.setProjectId(knowledge.getProjectId());
        vo.setKnowledgeContent(knowledge.getKnowledgeContent());
        vo.setCompleteContent(knowledge.getCompleteContent());
        vo.setMasteryLevel(knowledge.getMasteryLevel());
        vo.setMasteryScore(knowledge.getMasteryScore());
        vo.setIsCompleted(knowledge.getIsCompleted());
        vo.setSourceType(knowledge.getSourceType());
        vo.setFileSources(Collections.emptyList());
        vo.setSuggestions(Collections.emptyList());
        return vo;
    }

    private String buildCacheKey(Long userId, KnowledgeQueryParam dto, int page, int pageSize) {
        try {
            String input = userId + ":" + (dto.getKeyword() != null ? dto.getKeyword() : "")
                    + ":" + (dto.getTag() != null ? dto.getTag() : "")
                    + ":" + (dto.getDomain() != null ? dto.getDomain() : "")
                    + ":" + (dto.getProjectId() != null ? dto.getProjectId() : "")
                    + ":" + (dto.getMasteryLevel() != null ? dto.getMasteryLevel() : "")
                    + ":" + page
                    + ":" + pageSize;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return com.knowledgestarmap.util.RedisKeyBuilder.searchResult(userId, sb.toString());
        } catch (Exception e) {
            log.warn("计算检索缓存key失败", e);
            return com.knowledgestarmap.util.RedisKeyBuilder.searchResult(userId, "default");
        }
    }
}
