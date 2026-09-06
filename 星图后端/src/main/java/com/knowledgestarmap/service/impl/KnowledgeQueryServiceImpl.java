package com.knowledgestarmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.KnowledgeQueryDTO;
import com.knowledgestarmap.entity.FileKnowledgeDO;
import com.knowledgestarmap.entity.FileResourceDO;
import com.knowledgestarmap.entity.KnowledgeCompleteLogDO;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import com.knowledgestarmap.entity.KnowledgeSuggestionDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.FileKnowledgeMapper;
import com.knowledgestarmap.mapper.FileResourceMapper;
import com.knowledgestarmap.mapper.KnowledgeCompleteLogMapper;
import com.knowledgestarmap.mapper.KnowledgeInfoMapper;
import com.knowledgestarmap.mapper.KnowledgeSuggestionMapper;
import com.knowledgestarmap.service.KnowledgeQueryService;
import com.knowledgestarmap.util.KnowledgeHashUtil;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.FileKnowledgeVO;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import com.knowledgestarmap.vo.KnowledgeSuggestionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeQueryServiceImpl implements KnowledgeQueryService {

    private final KnowledgeInfoMapper knowledgeInfoMapper;
    private final FileKnowledgeMapper fileKnowledgeMapper;
    private final FileResourceMapper fileResourceMapper;
    private final KnowledgeSuggestionMapper knowledgeSuggestionMapper;
    private final KnowledgeCompleteLogMapper knowledgeCompleteLogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public KnowledgeQueryServiceImpl(KnowledgeInfoMapper knowledgeInfoMapper,
                                     FileKnowledgeMapper fileKnowledgeMapper,
                                     FileResourceMapper fileResourceMapper,
                                     KnowledgeSuggestionMapper knowledgeSuggestionMapper,
                                     KnowledgeCompleteLogMapper knowledgeCompleteLogMapper,
                                     StringRedisTemplate stringRedisTemplate) {
        this.knowledgeInfoMapper = knowledgeInfoMapper;
        this.fileKnowledgeMapper = fileKnowledgeMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.knowledgeSuggestionMapper = knowledgeSuggestionMapper;
        this.knowledgeCompleteLogMapper = knowledgeCompleteLogMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public PageResult<KnowledgeInfoVO> list(KnowledgeQueryDTO dto, Long userId) {
        int page = Math.max(1, dto.getPage() != null ? dto.getPage() : 1);
        int pageSize = Math.min(100, Math.max(1, dto.getPageSize() != null ? dto.getPageSize() : 20));

        LambdaQueryWrapper<KnowledgeInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeInfoDO::getUserId, userId)
                .eq(dto.getTag() != null, KnowledgeInfoDO::getKnowledgeTag, dto.getTag())
                .eq(dto.getMasteryLevel() != null, KnowledgeInfoDO::getMasteryLevel, dto.getMasteryLevel())
                .orderByDesc(KnowledgeInfoDO::getCreateTime);

        Page<KnowledgeInfoDO> pageResult = knowledgeInfoMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<KnowledgeInfoVO> voList = pageResult.getRecords().stream()
                .map(this::toVOWithoutRelations)
                .collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), page, pageSize, voList);
    }

    @Override
    public PageResult<KnowledgeInfoVO> search(KnowledgeQueryDTO dto, Long userId) {
        int page = Math.max(1, dto.getPage() != null ? dto.getPage() : 1);
        int pageSize = Math.min(100, Math.max(1, dto.getPageSize() != null ? dto.getPageSize() : 20));

        LambdaQueryWrapper<KnowledgeInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeInfoDO::getUserId, userId);
        if (dto.getKeyword() != null) {
            wrapper.and(w -> w.like(KnowledgeInfoDO::getKnowledgeName, dto.getKeyword())
                    .or().like(KnowledgeInfoDO::getKnowledgeContent, dto.getKeyword())
                    .or().like(KnowledgeInfoDO::getKnowledgeTag, dto.getKeyword()));
        }
        if (dto.getTag() != null) {
            wrapper.and(w -> w.eq(KnowledgeInfoDO::getKnowledgeTag, dto.getTag())
                    .or().like(KnowledgeInfoDO::getKnowledgeTag, dto.getTag()));
        }
        wrapper.eq(dto.getMasteryLevel() != null, KnowledgeInfoDO::getMasteryLevel, dto.getMasteryLevel())
                .orderByDesc(KnowledgeInfoDO::getCreateTime);

        Page<KnowledgeInfoDO> pageResult = knowledgeInfoMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<KnowledgeInfoVO> voList = pageResult.getRecords().stream().map(k -> {
            KnowledgeInfoVO vo = toVOWithoutRelations(k);
            List<FileKnowledgeDO> fileKnowledges = fileKnowledgeMapper.selectList(
                    new LambdaQueryWrapper<FileKnowledgeDO>()
                            .eq(FileKnowledgeDO::getKnowledgeId, k.getId())
                            .eq(FileKnowledgeDO::getUserId, userId)
            );
            if (!fileKnowledges.isEmpty()) {
                FileKnowledgeDO fk = fileKnowledges.get(0);
                vo.setContentSegment(fk.getContentSegment());
            }
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), page, pageSize, voList);
    }

    @Override
    public KnowledgeInfoVO getById(Long knowledgeId, Long userId) {
        KnowledgeInfoDO knowledge = knowledgeInfoMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getId, knowledgeId)
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
        );
        if (knowledge == null) {
            throw new BizException(BizErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        return buildKnowledgeVO(knowledge);
    }

    @Override
    public List<KnowledgeInfoVO> getByFileId(Long fileId, Long userId) {
        List<FileKnowledgeDO> fileKnowledges = fileKnowledgeMapper.selectList(
                new LambdaQueryWrapper<FileKnowledgeDO>()
                        .eq(FileKnowledgeDO::getFileId, fileId)
                        .eq(FileKnowledgeDO::getUserId, userId)
        );

        if (fileKnowledges.isEmpty()) return List.of();

        List<Long> knowledgeIds = fileKnowledges.stream()
                .map(FileKnowledgeDO::getKnowledgeId)
                .collect(Collectors.toList());

        List<KnowledgeInfoDO> knowledgeList = knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .in(KnowledgeInfoDO::getId, knowledgeIds)
                        .eq(KnowledgeInfoDO::getUserId, userId)
        );

        return knowledgeList.stream().map(k -> {
            KnowledgeInfoVO vo = toVOWithRelations(k);
            List<FileKnowledgeVO> sources = fileKnowledges.stream()
                    .filter(fk -> fk.getKnowledgeId().equals(k.getId()))
                    .map(fk -> {
                        FileKnowledgeVO fkvo = new FileKnowledgeVO();
                        fkvo.setKnowledgeId(fk.getKnowledgeId());
                        fkvo.setFileId(fk.getFileId());
                        fkvo.setContentSegment(fk.getContentSegment());
                        fkvo.setSegmentStart(fk.getSegmentStart());
                        fkvo.setSegmentEnd(fk.getSegmentEnd());
                        return fkvo;
                    })
                    .collect(Collectors.toList());
            vo.setFileSources(sources);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeInfoVO> getByProjectId(Long projectId, Long userId) {
        List<KnowledgeInfoDO> knowledgeList = knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getProjectId, projectId)
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
                        .orderByDesc(KnowledgeInfoDO::getCreateTime)
        );
        return knowledgeList.stream().map(this::buildKnowledgeVO).collect(Collectors.toList());
    }

    @Override
    public KnowledgeInfoVO createKnowledge(KnowledgeInfoDO knowledgeInfo, Long userId) {
        knowledgeInfo.setUserId(userId);
        knowledgeInfo.setSourceType(2);
        knowledgeInfo.setIsCompleted(0);
        knowledgeInfoMapper.insert(knowledgeInfo);
        return getById(knowledgeInfo.getId(), userId);
    }

    @Override
    public void updateKnowledge(KnowledgeInfoDO knowledgeInfo, Long userId) {
        KnowledgeInfoDO existing = knowledgeInfoMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getId, knowledgeInfo.getId())
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
        );
        if (existing == null) {
            throw new BizException(BizErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        if (knowledgeInfo.getKnowledgeName() != null && !knowledgeInfo.getKnowledgeName().isBlank()) {
            existing.setKnowledgeName(knowledgeInfo.getKnowledgeName());
        }
        if (knowledgeInfo.getKnowledgeDomain() != null) {
            existing.setKnowledgeDomain(knowledgeInfo.getKnowledgeDomain());
        }
        if (knowledgeInfo.getKnowledgeTag() != null && !knowledgeInfo.getKnowledgeTag().isBlank()) {
            existing.setKnowledgeTag(knowledgeInfo.getKnowledgeTag());
        }
        if (knowledgeInfo.getProjectId() != null) {
            existing.setProjectId(knowledgeInfo.getProjectId());
        }
        if (knowledgeInfo.getMasteryScore() != null) {
            existing.setMasteryScore(knowledgeInfo.getMasteryScore());
            existing.setMasteryLevel(calcMasteryLevel(knowledgeInfo.getMasteryScore()));
        }
        if (knowledgeInfo.getMasteryLevel() != null) {
            existing.setMasteryLevel(knowledgeInfo.getMasteryLevel());
            existing.setMasteryScore(levelToScore(knowledgeInfo.getMasteryLevel()));
        }
        if (knowledgeInfo.getKnowledgeContent() != null) {
            if (knowledgeInfo.getKnowledgeContent().length() > 10000) {
                throw new BizException(BizErrorCode.KNOWLEDGE_CONTENT_TOO_LONG);
            }
            existing.setKnowledgeContent(knowledgeInfo.getKnowledgeContent());
        }
        if (knowledgeInfo.getCompleteContent() != null) {
            existing.setCompleteContent(knowledgeInfo.getCompleteContent());
        }

        // 名称或内容变化后重算去重哈希，保证后续去重判断准确
        existing.setKnowledgeHash(KnowledgeHashUtil.computeHash(
                existing.getKnowledgeName(), existing.getKnowledgeContent()));

        knowledgeInfoMapper.updateById(existing);

        evictSearchCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long knowledgeId, Long userId) {
        KnowledgeInfoDO knowledge = knowledgeInfoMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .eq(KnowledgeInfoDO::getId, knowledgeId)
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
        );
        if (knowledge == null) {
            throw new BizException(BizErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        knowledgeInfoMapper.deleteById(knowledgeId);

        fileKnowledgeMapper.delete(
                new LambdaQueryWrapper<FileKnowledgeDO>()
                        .eq(FileKnowledgeDO::getKnowledgeId, knowledgeId)
                        .eq(FileKnowledgeDO::getUserId, userId)
        );

        knowledgeSuggestionMapper.delete(
                new LambdaQueryWrapper<KnowledgeSuggestionDO>()
                        .eq(KnowledgeSuggestionDO::getKnowledgeId, knowledgeId)
                        .eq(KnowledgeSuggestionDO::getUserId, userId)
        );

        knowledgeCompleteLogMapper.delete(
                new LambdaQueryWrapper<KnowledgeCompleteLogDO>()
                        .eq(KnowledgeCompleteLogDO::getKnowledgeId, knowledgeId)
                        .eq(KnowledgeCompleteLogDO::getUserId, userId)
        );

        evictSearchCache(userId);
    }

    private void evictSearchCache(Long userId) {
        try {
            Set<String> keys = stringRedisTemplate.keys(RedisKeyBuilder.searchResult(userId, "*"));
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清除检索缓存失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    private KnowledgeInfoVO buildKnowledgeVO(KnowledgeInfoDO knowledge) {
        KnowledgeInfoVO vo = toVOWithRelations(knowledge);

        List<FileKnowledgeDO> fileKnowledges = fileKnowledgeMapper.selectList(
                new LambdaQueryWrapper<FileKnowledgeDO>()
                        .eq(FileKnowledgeDO::getKnowledgeId, knowledge.getId())
                        .eq(FileKnowledgeDO::getUserId, knowledge.getUserId())
        );

        if (!fileKnowledges.isEmpty()) {
            List<Long> fileIds = fileKnowledges.stream().map(FileKnowledgeDO::getFileId).distinct().collect(Collectors.toList());
            java.util.Map<Long, FileResourceDO> fileMap = fileResourceMapper.selectList(
                    new LambdaQueryWrapper<FileResourceDO>().in(FileResourceDO::getId, fileIds).eq(FileResourceDO::getUserId, knowledge.getUserId())
            ).stream().collect(Collectors.toMap(FileResourceDO::getId, f -> f, (a, b) -> a));

            List<FileKnowledgeVO> sources = fileKnowledges.stream().map(fk -> {
                FileKnowledgeVO fkvo = new FileKnowledgeVO();
                fkvo.setKnowledgeId(fk.getKnowledgeId());
                fkvo.setFileId(fk.getFileId());
                fkvo.setContentSegment(fk.getContentSegment());
                fkvo.setSegmentStart(fk.getSegmentStart());
                fkvo.setSegmentEnd(fk.getSegmentEnd());
                FileResourceDO fr = fileMap.get(fk.getFileId());
                if (fr != null) {
                    fkvo.setFileName(fr.getFileName());
                    fkvo.setFilePath(fr.getFilePath());
                }
                return fkvo;
            }).collect(Collectors.toList());
            vo.setFileSources(sources);
        }

        List<KnowledgeSuggestionDO> suggestions = knowledgeSuggestionMapper.selectList(
                new LambdaQueryWrapper<KnowledgeSuggestionDO>()
                        .eq(KnowledgeSuggestionDO::getKnowledgeId, knowledge.getId())
                        .eq(KnowledgeSuggestionDO::getUserId, knowledge.getUserId())
        );

        if (!suggestions.isEmpty()) {
            List<KnowledgeSuggestionVO> suggestionVOs = suggestions.stream().map(s -> {
                KnowledgeSuggestionVO svo = new KnowledgeSuggestionVO();
                svo.setId(s.getId());
                svo.setKnowledgeId(s.getKnowledgeId());
                svo.setSuggestionType(s.getSuggestionType());
                svo.setSuggestionTitle(s.getSuggestionTitle());
                svo.setSuggestionContent(s.getSuggestionContent());
                svo.setSuggestionReason(s.getSuggestionReason());
                svo.setStatus(s.getStatus());
                svo.setCreateTime(s.getCreateTime());
                return svo;
            }).collect(Collectors.toList());
            vo.setSuggestions(suggestionVOs);
        }

        return vo;
    }

    private KnowledgeInfoVO toVOWithoutRelations(KnowledgeInfoDO knowledge) {
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
        return vo;
    }

    private KnowledgeInfoVO toVOWithRelations(KnowledgeInfoDO knowledge) {
        KnowledgeInfoVO vo = toVOWithoutRelations(knowledge);
        vo.setContentSegment(null);
        return vo;
    }

    private int calcMasteryLevel(BigDecimal score) {
        if (score == null) return 1;
        if (score.compareTo(new BigDecimal("90")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("70")) >= 0) return 2;
        return 1;
    }

    private BigDecimal levelToScore(int level) {
        switch (level) {
            case 3:
                return new BigDecimal("95");
            case 2:
                return new BigDecimal("80");
            default:
                return new BigDecimal("50");
        }
    }

    @Override
    public java.util.List<String> listDistinctTags(Long userId) {
        return knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .select(KnowledgeInfoDO::getKnowledgeTag)
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
                        .isNotNull(KnowledgeInfoDO::getKnowledgeTag)
        ).stream()
                .map(KnowledgeInfoDO::getKnowledgeTag)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public java.util.List<String> listDistinctDomains(Long userId) {
        return knowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<KnowledgeInfoDO>()
                        .select(KnowledgeInfoDO::getKnowledgeDomain)
                        .eq(KnowledgeInfoDO::getUserId, userId)
                        .eq(KnowledgeInfoDO::getIsDeleted, 0)
                        .isNotNull(KnowledgeInfoDO::getKnowledgeDomain)
        ).stream()
                .map(KnowledgeInfoDO::getKnowledgeDomain)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
