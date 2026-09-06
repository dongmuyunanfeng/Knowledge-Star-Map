package com.knowledgestarmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.entity.SearchHistoryDO;
import com.knowledgestarmap.mapper.SearchHistoryMapper;
import com.knowledgestarmap.service.SearchHistoryService;
import com.knowledgestarmap.vo.SearchHistoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private static final int MAX_HISTORY_SIZE = 50;

    private final SearchHistoryMapper searchHistoryMapper;

    public SearchHistoryServiceImpl(SearchHistoryMapper searchHistoryMapper) {
        this.searchHistoryMapper = searchHistoryMapper;
    }

    @Override
    public void addHistory(Long userId, String keyword, int resultCount) {
        SearchHistoryDO history = new SearchHistoryDO();
        history.setUserId(userId);
        history.setKeyword(keyword);
        history.setResultCount(resultCount);
        searchHistoryMapper.insert(history);

        LambdaQueryWrapper<SearchHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistoryDO::getUserId, userId)
                .orderByAsc(SearchHistoryDO::getCreateTime)
                .last("LIMIT 1");
        SearchHistoryDO oldest = searchHistoryMapper.selectOne(wrapper);
        if (oldest != null) {
            Long count = searchHistoryMapper.selectCount(
                    new LambdaQueryWrapper<SearchHistoryDO>()
                            .eq(SearchHistoryDO::getUserId, userId)
            );
            if (count > MAX_HISTORY_SIZE) {
                searchHistoryMapper.deleteById(oldest.getId());
            }
        }
    }

    @Override
    public PageResult<SearchHistoryVO> listHistory(Long userId, int page, int pageSize) {
        Page<SearchHistoryDO> pageResult = searchHistoryMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<SearchHistoryDO>()
                        .eq(SearchHistoryDO::getUserId, userId)
                        .orderByDesc(SearchHistoryDO::getCreateTime)
        );

        List<SearchHistoryVO> voList = pageResult.getRecords().stream().map(h -> {
            SearchHistoryVO vo = new SearchHistoryVO();
            vo.setId(h.getId());
            vo.setKeyword(h.getKeyword());
            vo.setResultCount(h.getResultCount());
            vo.setCreateTime(h.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(pageResult.getTotal(), page, pageSize, voList);
    }

    @Override
    public void deleteHistory(Long historyId, Long userId) {
        Long count = searchHistoryMapper.selectCount(
                new LambdaQueryWrapper<SearchHistoryDO>()
                        .eq(SearchHistoryDO::getId, historyId)
                        .eq(SearchHistoryDO::getUserId, userId)
        );
        if (count == 0) {
            return;
        }
        searchHistoryMapper.deleteById(historyId);
    }
}
