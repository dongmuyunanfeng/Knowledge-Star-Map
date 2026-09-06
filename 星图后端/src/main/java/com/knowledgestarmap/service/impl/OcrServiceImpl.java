package com.knowledgestarmap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgestarmap.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 图片OCR：调用视觉模型（默认 glm-4v-flash）识别图片文字。
 * 配置独立于模型1（app.agent.llm-*）与模型2（app.agent.chat-llm-*），读 app.agent.ocr-llm-*。
 */
@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    private static final String OCR_PROMPT = """
            请完整、准确地识别图片中的所有文字内容，包括标题、正文、代码、表格、公式等。\
            按原文顺序输出纯文本，不要添加任何解释、评论或总结。若图片中没有文字，只输出「无文字」。""";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.agent.ocr-llm-api-url}")
    private String apiUrl;

    @Value("${app.agent.ocr-llm-api-key}")
    private String apiKey;

    @Value("${app.agent.ocr-llm-model:glm-4v-flash}")
    private String model;

    @Value("${app.agent.ocr-llm-timeout-ms:120000}")
    private long timeoutMs;

    public OcrServiceImpl() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String recognize(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OCR 未配置 apiKey，无法识别图片");
            return null;
        }

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> imageUrl = Map.of("url", dataUrl);
        Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", imageUrl);
        Map<String, Object> textPart = Map.of("type", "text", "text", OCR_PROMPT);
        Map<String, Object> userMessage = Map.of("role", "user", "content", List.of(textPart, imagePart));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(userMessage),
                "temperature", 0.1,
                "max_tokens", 4096
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        try {
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("OCR 调用非2xx: status={}", response.getStatusCode());
                return null;
            }
            String content = parseContent(response.getBody());
            if (content == null || content.isBlank() || "无文字".equals(content.trim())) {
                return null;
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("OCR 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseContent(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
            JsonNode msg = choices.get(0).get("message");
            if (msg == null) return null;
            JsonNode content = msg.get("content");
            if (content == null) return null;
            if (content.isTextual()) {
                return content.asText();
            }
            // 部分视觉模型 content 是数组，取其中 text 片段拼接
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    JsonNode t = part.get("text");
                    if (t != null && t.isTextual()) {
                        sb.append(t.asText()).append('\n');
                    }
                }
                return sb.toString().trim();
            }
            return null;
        } catch (Exception e) {
            log.warn("OCR 响应解析失败: {}", e.getMessage());
            return null;
        }
    }
}
