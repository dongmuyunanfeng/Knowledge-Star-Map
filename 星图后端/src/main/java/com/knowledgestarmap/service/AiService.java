package com.knowledgestarmap.service;

public interface AiService {
    String callCompletion(String systemPrompt, String userMessage,
                          double temperature, int maxTokens, long timeoutMs);
}
