package com.knowledgestarmap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgestarmap.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识提取助手。你的任务是将用户的学习资料按照语义内容拆分为独立的知识点，\
            并为每个知识点打上准确的技术领域标签。""";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.agent.llm-api-url}")
    private String llmApiUrl;

    @Value("${app.agent.llm-api-key}")
    private String llmApiKey;

    @Value("${app.agent.llm-model:student}")
    private String llmModel;

    public AiServiceImpl() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String callCompletion(String systemPrompt, String userMessage,
                                  double temperature, int maxTokens, long timeoutMs) {
        String effectiveSystem = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt : SYSTEM_PROMPT;

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", llmModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", effectiveSystem),
                Map.of("role", "user", "content", userMessage)
        ));
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        // 不设置response_format，因为LLM提供商不支持该参数

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + llmApiKey);
        HttpEntity<String> entity = new HttpEntity<>(
                toJsonOrEmpty(requestBody), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    llmApiUrl, HttpMethod.POST, entity, String.class);
            return parseContent(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("LLM API 错误: status={}, body={}", e.getStatusCode(),
                    truncate(e.getResponseBodyAsString(), 500));
            if (e.getStatusCode().is4xxClientError()) {
                return retryWithoutResponseFormat(requestBody, headers);
            }
            return "";
        } catch (Exception e) {
            log.error("LLM 调用异常: {}", e.getMessage());
            return "";
        }
    }

    private String retryWithoutResponseFormat(Map<String, Object> requestBody, HttpHeaders headers) {
        Map<String, Object> retryBody = new java.util.HashMap<>(requestBody);
        retryBody.remove("response_format");
        try {
            HttpEntity<String> retryEntity = new HttpEntity<>(toJsonOrEmpty(retryBody), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    llmApiUrl, HttpMethod.POST, retryEntity, String.class);
            log.info("LLM 重试成功（去除response_format）");
            return parseContent(response.getBody());
        } catch (Exception ex) {
            log.error("LLM 重试（去除response_format）失败: {}", ex.getMessage());
            return "";
        }
    }

    private String toJsonOrEmpty(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private String parseContent(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return "";
            JsonNode msg = choices.get(0).get("message");
            if (msg == null) return "";
            String content = msg.get("content") != null
                    ? msg.get("content").asText() : "";
            return content.trim();
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败: {}", e.getMessage());
            return "";
        }
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "..." : s);
    }
}
