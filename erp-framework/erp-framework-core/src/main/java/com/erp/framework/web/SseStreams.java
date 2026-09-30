package com.erp.framework.web;

import com.erp.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 流式输出（Server-Sent Events）工具：在独立线程中执行任务（继承当前登录用户的安全上下文），任务通过 {@link Sender} 推送事件。
 *
 * <p>事件约定：任务自定义的事件名 + JSON 数据；任务正常结束由调用方推送 done 事件；抛出异常时推送 {@code error} 事件
 * （业务异常带 message，其他异常为通用提示）并结束。客户端断开后 {@link Sender#send} 返回 false，任务应尽快结束。
 */
@Component
public class SseStreams {

    private static final Logger log = LoggerFactory.getLogger(SseStreams.class);

    /** 事件发送器 */
    public interface Sender {
        /** @return false 表示客户端已断开 */
        boolean send(String event, Object data);
    }

    private final ExecutorService pool;

    public SseStreams() {
        AtomicInteger n = new AtomicInteger();
        this.pool = new DelegatingSecurityContextExecutorService(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "erp-sse-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));
    }

    public SseEmitter stream(long timeoutMillis, Consumer<Sender> job) {
        // 反向代理（Nginx）默认缓冲响应，SSE 需要关闭缓冲才能实时送达
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a && a.getResponse() != null) {
            a.getResponse().setHeader("X-Accel-Buffering", "no");
            a.getResponse().setHeader("Cache-Control", "no-cache");
        }
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        boolean[] closed = {false};
        emitter.onCompletion(() -> closed[0] = true);
        emitter.onTimeout(() -> closed[0] = true);
        emitter.onError(e -> closed[0] = true);
        Sender sender = (event, data) -> {
            if (closed[0]) return false;
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
                return true;
            } catch (IOException | IllegalStateException e) {
                closed[0] = true;
                return false;
            }
        };
        pool.execute(() -> {
            try {
                job.accept(sender);
            } catch (BizException e) {
                sender.send("error", java.util.Map.of("message", e.getMessage() == null ? "操作失败" : e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("[SSE] 任务失败", e);
                sender.send("error", java.util.Map.of("message", "服务暂时不可用，请稍后再试"));
            } finally {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // 已断开
                }
            }
        });
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
