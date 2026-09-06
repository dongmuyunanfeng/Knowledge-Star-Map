package com.knowledgestarmap.enums;

import lombok.Getter;

@Getter
public enum BizErrorCode {
    // 通用
    SUCCESS(200, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // 用户 1001-1099
    USER_ALREADY_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    TOKEN_EXPIRED(1004, "Token已过期"),

    // 文件 1101-1199
    FILE_INVALID_TYPE(1101, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(1102, "文件大小超出限制"),
    FILE_NOT_FOUND(1103, "文件不存在"),
    FILE_PARSE_FAILED(1104, "文件解析失败"),

    // 知识点 1201-1299
    KNOWLEDGE_NOT_FOUND(1201, "知识点不存在"),
    KNOWLEDGE_DUPLICATE(1202, "知识点重复"),
    KNOWLEDGE_TAG_REQUIRED(1203, "知识点标签不能为空"),
    KNOWLEDGE_CONTENT_TOO_LONG(1204, "知识点内容超出长度限制（最大10000字符）"),

    // 补全建议 1301-1399
    SUGGESTION_NOT_FOUND(1301, "建议不存在"),
    SUGGESTION_ALREADY_PROCESSED(1302, "建议已处理，不能重复操作"),
    KNOWLEDGE_NOT_COMPLETABLE(1303, "该知识点暂不需要补全"),
    SUGGESTION_ALREADY_EXISTS(1304, "该知识点已有待确认建议"),

    // Agent会话 1401-1499
    SESSION_NOT_FOUND(1401, "会话不存在"),
    SESSION_ALREADY_PINNED(1402, "会话已标记为重要"),
    SESSION_EXPIRED(1403, "会话已过期"),
    AGENT_MAX_ROUNDS_EXCEEDED(1404, "Agent推理轮次已达上限"),

    // 缓存 1801-1899
    CACHE_CONNECTION_FAILED(1801, "缓存服务连接失败"),
    CACHE_OPERATION_FAILED(1802, "缓存操作失败"),
    CONFIG_READ_ERROR(1803, "配置读取失败"),
    CONFIG_WRITE_ERROR(1804, "配置写入失败"),

    // 学习规划 1601-1699
    PLAN_NOT_FOUND(1601, "学习计划不存在"),

    // 简历 1701-1799
    RESUME_NOT_FOUND(1701, "简历素材不存在"),
    RESUME_GENERATE_FAILED(1702, "简历生成失败，请稍后重试"),

    // LLM 1901-1999
    LLM_TIMEOUT(1901, "LLM 调用超时"),
    LLM_FORMAT_INVALID(1902, "LLM 返回格式异常"),
    LLM_RESPONSE_INVALID(1903, "LLM 返回内容不完整"),
    LLM_RATE_LIMIT(1904, "LLM API 限流"),
    SIMILARITY_SERVICE_FAIL(1905, "语义相似度服务异常，降级为哈希去重"),
    ;

    private final Integer code;
    private final String message;

    BizErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
