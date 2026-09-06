package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 模型2（GLM，tokenrhythm.studio OpenAI Completions 协议）统一客户端。
 *
 * 负责四功能点：Agent 对话、学习规划生成、简历生成、知识补全建议。
 * 模型1（知识点生成/文件解析）仍由 {@link AiServiceImpl} 读 app.agent.llm-*，两者配置彻底分离。
 */
@Slf4j
@Service
public class ChatLlmService {

    @Value("${app.agent.chat-llm-api-url}")
    private String apiUrl;

    @Value("${app.agent.chat-llm-api-key}")
    private String apiKey;

    @Value("${app.agent.chat-llm-model}")
    private String model;

    @Value("${app.agent.chat-llm-timeout-ms:120000}")
    private int timeoutMs;

    /** 服务端繁忙（502/503/504）时的最大重试次数。 */
    private static final int MAX_RETRIES = 2;

    private volatile RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(timeoutMs);
                    factory.setReadTimeout(timeoutMs);
                    restTemplate = new RestTemplate(factory);
                    restTemplate.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                        @Override
                        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                            return false;
                        }

                        @Override
                        public void handleError(org.springframework.http.client.ClientHttpResponse response) {
                        }
                    });
                }
            }
        }
        return restTemplate;
    }

    /** 单轮调用：system + user。返回 content，失败返回 null。 */
    public String chat(String systemPrompt, String userMessage, double temperature, int maxTokens) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);
        return chat(messages, temperature, maxTokens);
    }

    /** 多轮调用：完整 messages 列表。返回 content，失败返回 null。 */
    public String chat(List<Map<String, Object>> messages, double temperature, int maxTokens) {
        JSONObject message = doChat(messages, null, temperature, maxTokens);
        if (message == null) {
            return null;
        }
        String content = message.getString("content");
        return (content != null && !content.isBlank()) ? content.trim() : null;
    }

    /** 带工具的多轮调用：返回完整 assistant message（含 content 与 tool_calls）。失败返回 null。 */
    public JSONObject chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                    double temperature, int maxTokens) {
        return doChat(messages, tools, temperature, maxTokens);
    }

    /** 流式多轮调用（无工具）：边生成边回调 onDelta，返回完整 content，失败返回 null。 */
    public String chatStream(List<Map<String, Object>> messages, double temperature, int maxTokens,
                             Consumer<String> onDelta) {
        JSONObject message = chatWithToolsStream(messages, null, temperature, maxTokens, onDelta);
        if (message == null) {
            return null;
        }
        String content = message.getString("content");
        return (content != null && !content.isBlank()) ? content.trim() : null;
    }

    /** 流式单轮调用（无工具）：system + user，边生成边回调 onDelta。 */
    public String chatStream(String systemPrompt, String userMessage, double temperature, int maxTokens,
                             Consumer<String> onDelta) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);
        return chatStream(messages, temperature, maxTokens, onDelta);
    }

    /**
     * 流式带工具调用：边生成边回调 onDelta（仅 content 部分），
     * 返回完整 assistant message（content / reasoning_content / tool_calls）。失败返回 null。
     */
    public JSONObject chatWithToolsStream(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                          double temperature, int maxTokens, Consumer<String> onDelta) {
        Map<String, Object> requestBody = buildRequestBody(messages, tools, temperature, maxTokens);
        requestBody.put("stream", true);

        StreamAccumulator acc = null;
        try {
            acc = streamChat(requestBody, onDelta);
        } catch (Exception e) {
            log.warn("模型2流式调用异常，回退非流式: url={}, err={}", apiUrl, e.getMessage());
        }
        if (acc == null || acc.isEmpty()) {
            log.warn("模型2流式未返回有效数据，回退非流式");
            return doChat(messages, tools, temperature, maxTokens);
        }
        return acc.toMessage();
    }

    private JSONObject doChat(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                              double temperature, int maxTokens) {
        Map<String, Object> requestBody = buildRequestBody(messages, tools, temperature, maxTokens);

        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), buildHeaders());

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = getRestTemplate().exchange(apiUrl, HttpMethod.POST, entity, String.class);
                int status = response.getStatusCode().value();
                if ((status == 502 || status == 503 || status == 504) && attempt < MAX_RETRIES) {
                    log.warn("模型2服务繁忙(status={})，第{}次重试", status, attempt + 1);
                    sleepRetry(attempt);
                    continue;
                }
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    log.warn("模型2调用非2xx: status={}", response.getStatusCode());
                    return null;
                }
                JSONObject resp = JSON.parseObject(response.getBody().getBytes(StandardCharsets.UTF_8));
                if (resp == null) {
                    log.warn("模型2响应解析失败: body={}", truncate(response.getBody()));
                    return null;
                }
                var choices = resp.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    if (message != null) {
                        return message;
                    }
                }
                log.warn("模型2响应缺少 choices[0].message: body={}", truncate(response.getBody()));
                return null;
            } catch (Exception e) {
                log.warn("模型2调用失败: url={}, err={}", apiUrl, e.getMessage());
                return null;
            }
        }
        return null;
    }

    private StreamAccumulator streamChat(Map<String, Object> requestBody, Consumer<String> onDelta) {
        byte[] payload = JSON.toJSONString(requestBody).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = buildHeaders();

        return getRestTemplate().execute(apiUrl, HttpMethod.POST, request -> {
            request.getHeaders().addAll(headers);
            request.getBody().write(payload);
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("模型2流式调用非2xx: status={}", response.getStatusCode());
                return null;
            }
            StreamAccumulator acc = new StreamAccumulator();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) {
                            continue;
                        }
                        acc.consume(data, onDelta);
                    }
                }
            }
            return acc;
        });
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                                 double temperature, int maxTokens) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
        }
        return requestBody;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private void sleepRetry(int attempt) {
        try {
            Thread.sleep(2000L * (attempt + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** 流式增量累加器：合并 content / reasoning_content / tool_calls 的增量。 */
    private static class StreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();

        boolean isEmpty() {
            return content.length() == 0 && reasoning.length() == 0 && toolCalls.isEmpty();
        }

        void consume(String data, Consumer<String> onDelta) {
            JSONObject obj;
            try {
                obj = JSON.parseObject(data);
            } catch (Exception e) {
                return;
            }
            JSONArray choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            if (delta == null) {
                return;
            }

            String rc = delta.getString("reasoning_content");
            if (rc != null && !rc.isEmpty()) {
                reasoning.append(rc);
            }

            String c = delta.getString("content");
            if (c != null && !c.isEmpty()) {
                content.append(c);
                if (onDelta != null) {
                    onDelta.accept(c);
                }
            }

            JSONArray tcs = delta.getJSONArray("tool_calls");
            if (tcs != null) {
                for (int i = 0; i < tcs.size(); i++) {
                    JSONObject tc = tcs.getJSONObject(i);
                    if (tc == null) {
                        continue;
                    }
                    Integer idx = tc.getInteger("index");
                    if (idx == null) {
                        idx = 0;
                    }
                    toolCalls.computeIfAbsent(idx, k -> new ToolCallBuilder()).consume(tc);
                }
            }
        }

        JSONObject toMessage() {
            JSONObject msg = new JSONObject();
            if (content.length() > 0) {
                msg.put("content", content.toString());
            }
            if (reasoning.length() > 0) {
                msg.put("reasoning_content", reasoning.toString());
            }
            if (!toolCalls.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (ToolCallBuilder b : toolCalls.values()) {
                    arr.add(b.toJSON());
                }
                msg.put("tool_calls", arr);
            }
            return msg;
        }
    }

    /** 单个 tool_call 的增量合并器（arguments 分片拼接）。 */
    private static class ToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        void consume(JSONObject tc) {
            String tcId = tc.getString("id");
            if (tcId != null && !tcId.isEmpty()) {
                this.id = tcId;
            }
            JSONObject fn = tc.getJSONObject("function");
            if (fn != null) {
                String n = fn.getString("name");
                if (n != null && !n.isEmpty()) {
                    this.name = n;
                }
                String args = fn.getString("arguments");
                if (args != null) {
                    arguments.append(args);
                }
            }
        }

        JSONObject toJSON() {
            JSONObject tc = new JSONObject();
            if (id != null) {
                tc.put("id", id);
            }
            tc.put("type", "function");
            JSONObject fn = new JSONObject();
            if (name != null) {
                fn.put("name", name);
            }
            fn.put("arguments", arguments.toString());
            tc.put("function", fn);
            return tc;
        }
    }
}
