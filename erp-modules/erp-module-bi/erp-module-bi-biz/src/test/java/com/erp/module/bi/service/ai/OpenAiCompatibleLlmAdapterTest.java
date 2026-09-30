package com.erp.module.bi.service.ai;

import com.erp.module.bi.service.ai.LlmAdapter.LlmRequest;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolOutcome;
import com.erp.module.bi.service.ai.LlmAdapter.ToolSpec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OpenAI 兼容适配器：用本地模拟服务验证请求格式与工具调用循环（tool_calls → role=tool → 最终回答） */
class OpenAiCompatibleLlmAdapterTest {

    HttpServer server;
    final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    final List<String> auth = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger calls = new AtomicInteger();
    volatile int status = 200;

    static final String TOOL_CALL = """
            {"id":"c1","object":"chat.completion","model":"deepseek-chat","choices":[{"index":0,"finish_reason":"tool_calls",
              "message":{"role":"assistant","content":"","reasoning_content":"思考过程",
                "tool_calls":[{"id":"call_1","type":"function","function":{"name":"bi_query",
                  "arguments":"{\\"metrics\\":[\\"sales_ship_amount\\"],\\"from\\":\\"2026-08-01\\",\\"to\\":\\"2026-08-31\\"}"}}]}}],
             "usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}}""";
    static final String FINAL = """
            {"id":"c2","object":"chat.completion","model":"deepseek-chat","choices":[{"index":0,"finish_reason":"stop",
              "message":{"role":"assistant","content":"上个月出货额为 10,000 元。"}}],
             "usage":{"prompt_tokens":150,"completion_tokens":30,"total_tokens":180}}""";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = (status != 200 ? "{\"error\":{\"message\":\"Invalid API key\"}}" : calls.getAndIncrement() == 0 ? TOOL_CALL : FINAL)
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmRequest request(List<ToolSpec> tools) {
        return new LlmRequest("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "deepseek-chat", "sk-test", "系统提示", List.of(),
                "上个月出货额是多少", tools, 4096, 4, 60);
    }

    @Test
    void toolLoop() {
        List<Map<String, Object>> inputs = new ArrayList<>();
        ToolSpec spec = new ToolSpec("bi_query", "查询", Map.of("metrics", Map.of("type", "array", "items", Map.of("type", "string"))), List.of("metrics"));
        LlmResult r = new OpenAiCompatibleLlmAdapter().converse(request(List.of(spec)), (name, input) -> {
            inputs.add(input);
            return new ToolOutcome("{\"行数\":1}", false);
        });
        assertThat(r.text()).isEqualTo("上个月出货额为 10,000 元。");
        assertThat(r.stopReason()).isEqualTo("stop");
        assertThat(r.inputTokens()).isEqualTo(250);
        assertThat(r.outputTokens()).isEqualTo(50);
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).get("metrics")).isEqualTo(List.of("sales_ship_amount"));
        assertThat(auth).containsOnly("Bearer sk-test");
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("\"model\":\"deepseek-chat\"").contains("\"type\":\"function\"").contains("\"name\":\"bi_query\"")
                .contains("\"role\":\"system\"").contains("\"tool_choice\":\"auto\"");
        // 第二次请求：回传助手的 tool_calls（不含 reasoning_content），以 role=tool 返回结果
        assertThat(bodies.get(1)).contains("\"tool_call_id\":\"call_1\"").contains("\"role\":\"tool\"").contains("\"id\":\"call_1\"")
                .doesNotContain("reasoning_content");
    }

    @Test
    void httpErrorThrows() {
        status = 401;
        assertThatThrownBy(() -> new OpenAiCompatibleLlmAdapter().converse(request(List.of()), LlmAdapter.ToolHandler.NONE))
                .hasMessageContaining("HTTP 401").hasMessageContaining("Invalid API key");
        assertThat(bodies.get(0)).doesNotContain("\"tools\"");
    }
}
