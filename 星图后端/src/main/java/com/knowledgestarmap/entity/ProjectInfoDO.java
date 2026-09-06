package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_info")
public class ProjectInfoDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String projectName;
    private String projectDesc;
    private String projectTechStack;
    private String projectRole;
    private String projectHighlights;
    private String projectSourceFiles;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
