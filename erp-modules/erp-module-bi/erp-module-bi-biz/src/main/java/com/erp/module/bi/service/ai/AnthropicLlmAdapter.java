package com.erp.module.bi.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anthropic Messages API 适配器（官方 Java SDK）。工具调用循环：模型返回 tool_use 时执行全部工具调用，把所有 tool_result
 * 放在同一条 user 消息中返回；助手消息原样回传（保留思考块）。模型默认开启自适应思考，不另外设置 thinking 参数。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AnthropicLlmAdapter implements LlmAdapter {

    public static final String PROVIDER = "ANTHROPIC";

    /** 按 API Key + 超时缓存客户端（客户端内部持有连接池） */
    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public LlmResult converse(LlmRequest req, ToolHandler handler) {
        AnthropicClient client = client(req.apiKey(), req.timeoutSeconds());
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(req.model())
                .maxTokens(req.maxTokens())
                .system(req.system());
        for (ToolSpec t : req.tools()) {
            params.addTool(Tool.builder()
                    .name(t.name())
                    .description(t.description())
                    .inputSchema(schema(t))
                    .build());
        }
        for (Turn turn : req.history()) {
            if ("assistant".equals(turn.role())) params.addAssistantMessage(turn.text());
            else params.addUserMessage(turn.text());
        }
        params.addUserMessage(req.userMessage());

        long in = 0;
        long out = 0;
        String lastText = "";
        String stop = null;
        for (int round = 0; round <= req.maxRounds(); round++) {
            Message resp = client.messages().create(params.build());
            in += resp.usage().inputTokens();
            out += resp.usage().outputTokens();
            params.addMessage(resp);
            lastText = text(resp);
            Optional<StopReason> reason = resp.stopReason();
            stop = reason.map(StopReason::asString).orElse(null);
            if (reason.isPresent() && reason.get().equals(StopReason.PAUSE_TURN)) {
                continue;
            }
            List<ToolUseBlock> calls = resp.content().stream().flatMap(b -> b.toolUse().stream()).toList();
            if (calls.isEmpty() || reason.isEmpty() || !reason.get().equals(StopReason.TOOL_USE)) {
                return new LlmResult(lastText, stop, in, out);
            }
            if (round == req.maxRounds()) break;
            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock call : calls) {
                ToolOutcome outcome;
                try {
                    Map<String, Object> input = call._input().convert(new TypeReference<Map<String, Object>>() {
                    });
                    outcome = handler.handle(call.name(), input == null ? Map.of() : input);
                } catch (RuntimeException e) {
                    outcome = new ToolOutcome("工具参数无法解析：" + e.getMessage(), true);
                }
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(call.id())
                        .content(outcome.content())
                        .isError(outcome.error())
                        .build()));
            }
            params.addUserMessageOfBlockParams(results);
        }
        return new LlmResult(lastText, "tool_rounds_exceeded", in, out);
    }

    private AnthropicClient client(String apiKey, int timeoutSeconds) {
        return clients.computeIfAbsent(apiKey + "|" + timeoutSeconds, k -> AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(Optional.ofNullable(System.getenv("ERP_AI_BASE_URL")))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(1)
                .build());
    }

    /** 测试用：指定服务地址（本地模拟服务） */
    AnthropicClient clientFor(String apiKey, String baseUrl, int timeoutSeconds) {
        AnthropicClient c = AnthropicOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).timeout(Duration.ofSeconds(timeoutSeconds)).maxRetries(0)
                .build();
        clients.put(apiKey + "|" + timeoutSeconds, c);
        return c;
    }

    private static Tool.InputSchema schema(ToolSpec t) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        t.properties().forEach((name, schema) -> props.putAdditionalProperty(name, JsonValue.from(schema)));
        return Tool.InputSchema.builder().properties(props.build()).required(t.required()).build();
    }

    private static String text(Message resp) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : resp.content()) {
            b.text().ifPresent(t -> {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(t.text());
            });
        }
        return sb.toString();
    }
}
