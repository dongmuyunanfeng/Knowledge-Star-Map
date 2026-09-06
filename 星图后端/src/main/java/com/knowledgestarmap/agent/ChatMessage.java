package com.knowledgestarmap.agent;

import lombok.Data;

@Data
public class ChatMessage {
    private String role;
    private String content;
    private ToolCall toolCall;
    private ToolResult toolResult;
}
