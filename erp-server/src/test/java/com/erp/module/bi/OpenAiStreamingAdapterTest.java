package com.erp.module.bi;

import com.erp.module.bi.service.ai.LlmAdapter;
import com.erp.module.bi.service.ai.LlmAdapter.LlmRequest;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolOutcome;
import com.erp.module.bi.service.ai.OpenAiCompatibleLlmAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** OpenAI 兼容适配器的流式调用：分片的 tool_calls 拼接、文字逐段回调、usage 汇总 */
class OpenAiStreamingAdapterTest {

    private static String sse(String... chunks) {
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) sb.append("data: ").append(c).append("\n\n");
        return sb.append("data: [DONE]\n\n").toString();
    }

    @Test
    void streamsTextAndAssemblesToolCalls() throws IOException {
        AtomicInteger round = new AtomicInteger();
        List<String> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String payload = round.getAndIncrement() == 0
                    ? sse("{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"bi_query\",\"arguments\":\"{\\\"met\"}}]}}]}",
                    "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ric\\\":\\\"sales\\\"}\"}}]}}]}",
                    "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}")
                    : sse("{\"choices\":[{\"delta\":{\"content\":\"出货额\"}}]}", "{\"choices\":[{\"delta\":{\"content\":\"100 万\"}}]}",
                    "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8}}");
            byte[] b = payload.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        try {
            LlmRequest req = new LlmRequest("http://127.0.0.1:" + server.getAddress().getPort(), "m", "k", "sys", List.of(), "问题",
                    List.of(new LlmAdapter.ToolSpec("bi_query", "查询", Map.of(), List.of())), 100, 3, 10);
            List<String> pieces = new ArrayList<>();
            List<Map<String, Object>> toolInputs = new ArrayList<>();
            LlmResult r = new OpenAiCompatibleLlmAdapter().converse(req, (name, input) -> {
                toolInputs.add(input);
                return new ToolOutcome("{\"rows\":1}", false);
            }, pieces::add);
            assertThat(pieces).containsExactly("出货额", "100 万");
            assertThat(r.text()).isEqualTo("出货额100 万");
            assertThat(r.inputTokens()).isEqualTo(30);
            assertThat(r.outputTokens()).isEqualTo(13);
            assertThat(toolInputs).containsExactly(Map.of("metric", "sales"));
            assertThat(bodies.get(0)).contains("\"stream\":true").contains("include_usage");
            assertThat(bodies.get(1)).contains("call_1").contains("\"role\":\"tool\"");
        } finally {
            server.stop(0);
        }
    }
}
