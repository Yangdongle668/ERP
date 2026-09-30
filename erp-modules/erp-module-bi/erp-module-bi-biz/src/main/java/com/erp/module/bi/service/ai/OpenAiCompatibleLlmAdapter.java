package com.erp.module.bi.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI 兼容 Chat Completions 接口适配器（DeepSeek、通义千问 / 阿里云百炼兼容模式、其他兼容服务）。
 * <p>工具调用循环：模型返回 tool_calls 时逐个执行，以 role=tool 消息（tool_call_id 对应）返回结果，直到模型给出最终回答。
 * 回传助手消息时只保留 content 与 tool_calls（不回传 reasoning_content，DeepSeek 推理模型要求如此）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpenAiCompatibleLlmAdapter implements LlmAdapter {

    public static final String PROVIDER = "OPENAI_COMPATIBLE";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public LlmResult converse(LlmRequest req, ToolHandler handler) {
        return converse(req, handler, null);
    }

    @Override
    public LlmResult converse(LlmRequest req, ToolHandler handler, Consumer<String> onText) {
        ObjectNode body = json.createObjectNode();
        body.put("model", req.model());
        body.put("max_tokens", req.maxTokens());
        body.put("stream", onText != null);
        if (onText != null) body.putObject("stream_options").put("include_usage", true);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", req.system());
        for (Turn t : req.history()) {
            messages.addObject().put("role", "assistant".equals(t.role()) ? "assistant" : "user").put("content", t.text());
        }
        messages.addObject().put("role", "user").put("content", req.userMessage());
        if (!req.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec t : req.tools()) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("type", "object");
                params.put("properties", t.properties());
                params.put("required", t.required());
                ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
                fn.put("name", t.name()).put("description", t.description());
                fn.set("parameters", json.valueToTree(params));
            }
            body.put("tool_choice", "auto");
        }

        long in = 0;
        long out = 0;
        String lastText = "";
        for (int round = 0; round <= req.maxRounds(); round++) {
            JsonNode resp = onText != null ? postStream(req, body, onText) : post(req, body);
            JsonNode usage = resp.path("usage");
            in += usage.path("prompt_tokens").asLong(0);
            out += usage.path("completion_tokens").asLong(0);
            JsonNode choice = resp.path("choices").path(0);
            JsonNode msg = choice.path("message");
            String finish = choice.path("finish_reason").asText(null);
            lastText = msg.path("content").isTextual() ? msg.path("content").asText() : "";
            JsonNode calls = msg.path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) {
                return new LlmResult(lastText, "content_filter".equals(finish) ? "refusal" : finish, in, out);
            }
            if (round == req.maxRounds()) break;
            ObjectNode assistant = messages.addObject().put("role", "assistant");
            if (msg.path("content").isTextual()) assistant.put("content", msg.path("content").asText());
            else assistant.putNull("content");
            assistant.set("tool_calls", calls);
            for (JsonNode call : calls) {
                String name = call.path("function").path("name").asText();
                ToolOutcome outcome;
                try {
                    String args = call.path("function").path("arguments").asText("");
                    Map<String, Object> input = args.isBlank() ? Map.of() : json.readValue(args, new TypeReference<Map<String, Object>>() {
                    });
                    outcome = handler.handle(name, input == null ? Map.of() : input);
                } catch (IOException | RuntimeException e) {
                    outcome = new ToolOutcome("工具参数无法解析：" + e.getMessage(), true);
                }
                messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText())
                        .put("content", outcome.error() ? "错误：" + outcome.content() : outcome.content());
            }
        }
        return new LlmResult(lastText, "tool_rounds_exceeded", in, out);
    }

    private JsonNode post(LlmRequest req, ObjectNode body) {
        String base = req.baseUrl().endsWith("/") ? req.baseUrl().substring(0, req.baseUrl().length() - 1) : req.baseUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(req.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + req.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            return json.readTree(resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("调用大模型接口失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用大模型接口被中断", e);
        }
    }

    /**
     * 流式调用：读取 SSE（data: {...}），逐段回调文字，同时累积 content、tool_calls（按 index 拼接 arguments）、finish_reason、usage，
     * 合成与非流式响应相同结构的 JSON，供工具调用循环复用。
     */
    private JsonNode postStream(LlmRequest req, ObjectNode body, Consumer<String> onText) {
        String base = req.baseUrl().endsWith("/") ? req.baseUrl().substring(0, req.baseUrl().length() - 1) : req.baseUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofSeconds(req.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + req.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> resp = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (resp.statusCode() / 100 != 2) {
                String err = resp.body().collect(Collectors.joining("\n"));
                throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + truncate(err));
            }
            StringBuilder content = new StringBuilder();
            Map<Integer, ObjectNode> calls = new TreeMap<>();
            String[] finish = new String[1];
            JsonNode[] usage = new JsonNode[1];
            try (Stream<String> lines = resp.body()) {
                for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                    String line = it.next();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) break;
                    JsonNode chunk = json.readTree(data);
                    if (chunk.hasNonNull("usage")) usage[0] = chunk.get("usage");
                    JsonNode choice = chunk.path("choices").path(0);
                    if (choice.hasNonNull("finish_reason")) finish[0] = choice.get("finish_reason").asText();
                    JsonNode delta = choice.path("delta");
                    if (delta.path("content").isTextual() && !delta.get("content").asText().isEmpty()) {
                        String piece = delta.get("content").asText();
                        content.append(piece);
                        onText.accept(piece);
                    }
                    for (JsonNode tc : delta.path("tool_calls")) {
                        ObjectNode call = calls.computeIfAbsent(tc.path("index").asInt(0), k -> {
                            ObjectNode n = json.createObjectNode();
                            n.put("type", "function");
                            n.putObject("function").put("name", "").put("arguments", "");
                            return n;
                        });
                        if (tc.hasNonNull("id")) call.put("id", tc.get("id").asText());
                        ObjectNode fn = (ObjectNode) call.get("function");
                        if (tc.path("function").hasNonNull("name")) fn.put("name", fn.get("name").asText() + tc.get("function").get("name").asText());
                        if (tc.path("function").hasNonNull("arguments")) fn.put("arguments", fn.get("arguments").asText() + tc.get("function").get("arguments").asText());
                    }
                }
            }
            ObjectNode out = json.createObjectNode();
            ObjectNode choice = out.putArray("choices").addObject();
            ObjectNode message = choice.putObject("message");
            message.put("role", "assistant");
            if (content.length() > 0) message.put("content", content.toString());
            else message.putNull("content");
            if (!calls.isEmpty()) {
                ArrayNode arr = message.putArray("tool_calls");
                calls.values().forEach(arr::add);
            }
            if (finish[0] != null) choice.put("finish_reason", finish[0]);
            if (usage[0] != null) out.set("usage", usage[0]);
            return out;
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("调用大模型接口失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用大模型接口被中断", e);
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
