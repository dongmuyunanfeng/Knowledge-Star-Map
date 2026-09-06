package com.knowledgestarmap.vo;

import lombok.Data;

/** 路径B（项目经历生成）的 LLM 返回结果。 */
@Data
public class ProjectExperienceResult {
    private String responsibilities;

    public boolean isEmpty() {
        return responsibilities == null || responsibilities.isBlank();
    }
}
