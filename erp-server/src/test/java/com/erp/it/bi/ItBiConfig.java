package com.erp.it.bi;

import com.erp.module.bi.service.ai.LlmAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;

/** 测试用：替换大模型的脚本化适配器（AI-R06 LlmAdapter 可替换） */
@Configuration
class ItBiConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ScriptedLlm scriptedLlm() {
        return new ScriptedLlm();
    }

    /** 按顺序执行脚本步骤；没有脚本时按“#序号 解释”回复（异常解读）或返回固定文字 */
    static class ScriptedLlm implements LlmAdapter {

        final Deque<BiFunction<LlmRequest, ToolHandler, LlmResult>> script = new ConcurrentLinkedDeque<>();
        final List<LlmRequest> requests = Collections.synchronizedList(new ArrayList<>());
        final List<ToolOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String provider() {
            return "OPENAI_COMPATIBLE";
        }

        @Override
        public LlmResult converse(LlmRequest request, ToolHandler handler) {
            requests.add(request);
            BiFunction<LlmRequest, ToolHandler, LlmResult> step = script.pollFirst();
            if (step != null) {
                return step.apply(request, (name, input) -> {
                    ToolOutcome o = handler.handle(name, input);
                    outcomes.add(o);
                    return o;
                });
            }
            StringBuilder sb = new StringBuilder();
            for (String line : request.userMessage().split("\n")) {
                if (line.startsWith("#")) sb.append(line, 0, line.indexOf(' ')).append(" 模拟解释：").append(line.substring(line.indexOf(' ') + 1)).append('\n');
            }
            return new LlmResult(sb.isEmpty() ? "模拟总结" : sb.toString(), "end_turn", 10, 5);
        }

        void reset() {
            script.clear();
            requests.clear();
            outcomes.clear();
        }
    }
}
