package com.erp.module.bi.service.ai;

import java.util.List;
import java.util.Map;

/**
 * 大模型调用适配器（需求 13-04 AI-R06）。默认实现为 OpenAI 兼容的 Chat Completions 接口（{@link OpenAiCompatibleLlmAdapter}），
 * DeepSeek、通义千问等供应商都通过它接入；其他协议实现本接口并返回对应的 {@link #provider()} 即可；测试中可替换为模拟实现。
 *
 * <p>适配器负责完整的工具调用循环：模型请求调用工具时回调 {@link ToolHandler}，把结果交还模型，直到模型给出最终回答。
 */
public interface LlmAdapter {

    /** 调用协议编码（如 OPENAI_COMPATIBLE） */
    String provider();

    /** 执行一次对话（含工具调用循环）；网络错误、超时等抛出异常，由调用方记录并提示“AI 服务暂时不可用” */
    LlmResult converse(LlmRequest request, ToolHandler handler);

    /**
     * 流式对话：模型每输出一段文字就回调 onText（工具调用轮次的文字也会回调，最终以返回结果为准）。
     * 默认实现不支持流式，整段回答生成后一次回调；OpenAI 兼容适配器逐段回调。
     */
    default LlmResult converse(LlmRequest request, ToolHandler handler, java.util.function.Consumer<String> onText) {
        LlmResult r = converse(request, handler);
        if (onText != null && r.text() != null && !r.text().isEmpty()) onText.accept(r.text());
        return r;
    }

    /**
     * @param history   之前的对话轮次（只含文字）
     * @param maxTokens 每次调用的最大输出 token
     * @param maxRounds 最多工具调用轮次
     */
    record LlmRequest(String baseUrl, String model, String apiKey, String system, List<Turn> history, String userMessage, List<ToolSpec> tools, int maxTokens,
                      int maxRounds, int timeoutSeconds) {
    }

    /** role：user / assistant */
    record Turn(String role, String text) {
    }

    /** 工具定义：inputSchema 为 JSON Schema（type=object 的 properties / required） */
    record ToolSpec(String name, String description, Map<String, Object> properties, List<String> required) {
    }

    /** 工具执行结果：content 为交给模型的文本（通常为 JSON） */
    record ToolOutcome(String content, boolean error) {
    }

    @FunctionalInterface
    interface ToolHandler {
        ToolOutcome handle(String toolName, Map<String, Object> input);

        ToolHandler NONE = (name, input) -> new ToolOutcome("未知工具 " + name, true);
    }

    /**
     * @param stopReason end_turn / max_tokens / refusal / tool_rounds_exceeded 等
     */
    record LlmResult(String text, String stopReason, long inputTokens, long outputTokens) {
        public int totalTokens() {
            return (int) Math.min(Integer.MAX_VALUE, inputTokens + outputTokens);
        }
    }
}
