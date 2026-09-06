package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "file_resource", autoResultMap = true)
public class FileResourceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileSuffix;
    private String filePath;
    private Long fileSize;
    private String fileCategory;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String parseResult;
    private Integer parseStatus;
    private String parseMessage;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
