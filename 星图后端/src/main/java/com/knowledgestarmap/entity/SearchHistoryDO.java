package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("search_history")
public class SearchHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String keyword;
    private Integer resultCount;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
}
