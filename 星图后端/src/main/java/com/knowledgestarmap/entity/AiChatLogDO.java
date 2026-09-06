package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_log")
public class AiChatLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private String userQuestion;
    private String aiAnswer;
    private String chatType;
    private Integer isPinned;
    private String toolCallLog;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
}
