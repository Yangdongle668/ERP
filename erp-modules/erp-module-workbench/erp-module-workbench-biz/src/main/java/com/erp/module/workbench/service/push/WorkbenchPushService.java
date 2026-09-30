package com.erp.module.workbench.service.push;

import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.AlertResolvedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 待办 / 消息 / 预警的实时推送（SSE，需求 02 的“待办角标”）：前端订阅后，待办、消息、预警发生变化时收到 {@code refresh} 事件，
 * 再自行刷新角标与列表。事件与工作台存储监听同一批领域事件；写库在独立事务中完成，所以推送延后 {@value #DELAY_MS} 毫秒并合并同一用户的多次变化。
 *
 * <p>只推送到本节点上的连接。多实例部署时，订阅在其他节点的用户由前端的兜底轮询（连接正常时降为 5 分钟一次）刷新。
 */
@Service
public class WorkbenchPushService {

    static final long DELAY_MS = 400;
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
    /** 表示“所有在线用户” */
    private static final Long ALL = -1L;

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "erp-wb-push");
        t.setDaemon(true);
        return t;
    });

    public WorkbenchPushService() {
        // 心跳：保持连接（穿过代理的空闲超时），同时清理已断开的连接
        scheduler.scheduleWithFixedDelay(this::heartbeat, 25, 25, TimeUnit.SECONDS);
    }

    /** 订阅指定用户的推送 */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable remove = () -> {
            set.remove(emitter);
            if (set.isEmpty()) emitters.remove(userId, set);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("ts", System.currentTimeMillis()), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /** 在线连接数（监控 / 测试） */
    public int connections() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }

    // ==================== 事件 ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTodoCreated(TodoCreatedEvent e) {
        notifyUsers(e.getUserIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTodoDone(TodoDoneEvent e) {
        notifyUsers(e.getUserIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(MessageSendEvent e) {
        notifyUsers(e.getUserIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertRaised(AlertRaisedEvent e) {
        notifyUsers(e.getUserIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertResolved(AlertResolvedEvent e) {
        notifyUsers(null);
    }

    /** 通知指定用户；用户为空（如按权限接收、按 key 结束待办）时通知所有在线用户 */
    void notifyUsers(Collection<Long> userIds) {
        if (emitters.isEmpty()) return;
        if (userIds == null || userIds.isEmpty()) pending.add(ALL);
        else pending.addAll(userIds);
        scheduler.schedule(this::flush, DELAY_MS, TimeUnit.MILLISECONDS);
    }

    void flush() {
        Set<Long> targets = new HashSet<>();
        for (Long id : List.copyOf(pending)) {
            if (pending.remove(id)) targets.add(id);
        }
        if (targets.isEmpty()) return;
        Collection<Long> users = targets.contains(ALL) ? List.copyOf(emitters.keySet()) : targets;
        for (Long u : users) send(u, "refresh");
    }

    private void heartbeat() {
        for (Long u : List.copyOf(emitters.keySet())) send(u, "ping");
    }

    private void send(Long userId, String event) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null) return;
        for (SseEmitter em : set) {
            try {
                em.send(SseEmitter.event().name(event).data(Map.of("ts", System.currentTimeMillis()), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                set.remove(em);
                try {
                    em.complete();
                } catch (RuntimeException ignored) {
                    // 已断开
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        emitters.values().forEach(s -> s.forEach(e -> {
            try {
                e.complete();
            } catch (RuntimeException ignored) {
                // 忽略
            }
        }));
    }
}
