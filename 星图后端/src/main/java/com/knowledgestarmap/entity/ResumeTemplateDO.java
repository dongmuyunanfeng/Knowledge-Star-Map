package com.knowledgestarmap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_template")
public class ResumeTemplateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String templateName;
    private String techStack;
    private String skillDesc;
    private String projectHighlights;
    private String resumeSummary;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
