package com.erp.module.fx.service;

import com.erp.framework.maintenance.MaintenanceMode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 汇率轮询（需求 16-实时汇率 R02）：每 15 分钟取一次；失败后按 1、2、4、8…分钟指数退避重试，最长 60 分钟，成功后恢复 15 分钟。
 * 参数 fx.enabled 关闭、系统维护（备份恢复）期间暂停。配置 erp.fx.poll-enabled=false 时不启动（测试环境）。
 */
@Slf4j
@Component
public class FxPoller {

    static final Duration INTERVAL = FxService.CACHE_TTL;
    static final Duration MIN_BACKOFF = Duration.ofMinutes(1);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(60);

    private final FxService service;
    private final boolean enabled;
    private final Duration firstDelay;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fx-poller");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> next;

    public FxPoller(FxService service, @Value("${erp.fx.poll-enabled:true}") boolean enabled,
                    @Value("${erp.fx.first-delay-seconds:30}") long firstDelaySeconds) {
        this.service = service;
        this.enabled = enabled;
        this.firstDelay = Duration.ofSeconds(firstDelaySeconds);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("[实时汇率] 未启用轮询（erp.fx.poll-enabled=false）");
            return;
        }
        schedule(firstDelay);
    }

    /** 失败次数 n（≥1）对应的等待时间：1 分钟 × 2^(n-1)，最长 60 分钟 */
    static Duration backoff(int failures) {
        if (failures <= 0) return INTERVAL;
        long minutes = MIN_BACKOFF.toMinutes() << Math.min(failures - 1, 10);
        return Duration.ofMinutes(Math.min(minutes, MAX_BACKOFF.toMinutes()));
    }

    private synchronized void schedule(Duration delay) {
        if (executor.isShutdown()) return;
        next = executor.schedule(this::run, delay.toMillis(), TimeUnit.MILLISECONDS);
        service.pollState(true, LocalDateTime.now().plus(delay));
    }

    void run() {
        Duration delay = INTERVAL;
        try {
            if (MaintenanceMode.active()) delay = MIN_BACKOFF;
            else if (service.enabled()) service.refresh(false);
        } catch (Exception e) {
            delay = backoff(service.consecutiveFailures());
            log.info("[实时汇率] {} 分钟后重试", delay.toMinutes());
        } finally {
            schedule(delay);
        }
    }

    @PreDestroy
    public void stop() {
        if (next != null) next.cancel(false);
        executor.shutdownNow();
        service.pollState(false, null);
    }
}
