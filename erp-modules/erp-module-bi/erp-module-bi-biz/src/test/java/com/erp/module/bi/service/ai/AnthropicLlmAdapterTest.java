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

/** Anthropic 适配器：用本地模拟服务验证请求格式与工具调用循环（tool_use → tool_result → 最终回答） */
class AnthropicLlmAdapterTest {

    HttpServer server;
    final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger calls = new AtomicInteger();

    static final String TOOL_USE = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"tool_use","stop_sequence":null,
             "content":[{"type":"text","text":"查询中"},{"type":"tool_use","id":"toolu_1","name":"bi_query",
               "input":{"metrics":["sales_ship_amount"],"from":"2026-08-01","to":"2026-08-31"}}],
             "usage":{"input_tokens":100,"output_tokens":20}}""";
    static final String FINAL = """
            {"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"end_turn","stop_sequence":null,
             "content":[{"type":"text","text":"上个月出货额为 10,000 元。"}],
             "usage":{"input_tokens":150,"output_tokens":30}}""";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (calls.getAndIncrement() == 0 ? TOOL_USE : FINAL).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
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

    @Test
    void toolLoop() {
        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter();
        adapter.clientFor("sk-test", "http://127.0.0.1:" + server.getAddress().getPort(), 60);
        List<Map<String, Object>> inputs = new ArrayList<>();
        ToolSpec spec = new ToolSpec("bi_query", "查询", Map.of("metrics", Map.of("type", "array", "items", Map.of("type", "string"))), List.of("metrics"));
        LlmResult r = adapter.converse(new LlmRequest("claude-opus-5-5", "sk-test", "系统提示", List.of(), "上个月出货额是多少", List.of(spec), 4096, 4, 60),
                (name, input) -> {
                    inputs.add(input);
                    return new ToolOutcome("{\"行数\":1}", false);
                });
        assertThat(r.text()).isEqualTo("上个月出货额为 10,000 元。");
        assertThat(r.stopReason()).isEqualTo("end_turn");
        assertThat(r.inputTokens()).isEqualTo(250);
        assertThat(r.outputTokens()).isEqualTo(50);
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).get("metrics")).isEqualTo(List.of("sales_ship_amount"));
        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).contains("\"model\":\"claude-opus-5-5\"").contains("\"name\":\"bi_query\"").contains("系统提示")
                .doesNotContain("thinking");
        // 第二次请求：原样回传助手消息（含 tool_use），并在同一条 user 消息中返回 tool_result
        assertThat(bodies.get(1)).contains("\"tool_use_id\":\"toolu_1\"").contains("\"type\":\"tool_result\"").contains("\"id\":\"toolu_1\"");
    }
}
