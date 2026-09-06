package com.knowledgestarmap.agent;

import java.util.Map;

/** Agent工具接口，所有Function-Calling工具实现此接口 */
public interface AgentTool {
    /** 工具名称（对应name字段，如"knowledge_query"） */
    String name();

    /** 工具描述（用于LLM工具选择） */
    String description();

    /** 工具参数JSON Schema（用于LLM参数校验） */
    String parametersSchema();

    /** 执行工具，返回执行结果 */
    ToolExecutionResult execute(Map<String, Object> parameters, Long userId);
}
