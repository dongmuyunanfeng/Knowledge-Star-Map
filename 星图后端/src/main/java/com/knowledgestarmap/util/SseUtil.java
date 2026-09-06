package com.knowledgestarmap.util;

import com.alibaba.fastjson2.JSON;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/** SSE 事件输出工具，供控制器把 AI 生成过程推送到前端。 */
public final class SseUtil {

    private SseUtil() {
    }

    public static void event(PrintWriter writer, String event, Object data) {
        writer.write("event: " + event + "\n");
        writer.write("data: " + JSON.toJSONString(data) + "\n\n");
        writer.flush();
    }

    public static void progress(PrintWriter writer, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        event(writer, "progress", payload);
    }

    public static void delta(PrintWriter writer, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        event(writer, "delta", payload);
    }

    public static void done(PrintWriter writer, Object result) {
        event(writer, "done", result);
    }

    public static void error(PrintWriter writer, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        event(writer, "error", payload);
    }

    public static void error(PrintWriter writer, int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        event(writer, "error", payload);
    }
}
