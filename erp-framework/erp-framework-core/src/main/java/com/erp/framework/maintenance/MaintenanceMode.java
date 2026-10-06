package com.erp.framework.maintenance;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 维护模式（系统数据恢复期间）：{@link MaintenanceFilter} 拒绝除放行路径外的全部接口请求，定时任务跳过执行。
 * 单实例有效；多实例部署时恢复前应先停止其他实例。
 */
@Component
public class MaintenanceMode {

    /** 进入维护的原因与时间 */
    public record State(String reason, LocalDateTime since) {
    }

    private static final AtomicReference<State> STATE = new AtomicReference<>();

    /** @return 是否成功进入（已在维护中返回 false） */
    public boolean enter(String reason) {
        return STATE.compareAndSet(null, new State(reason, LocalDateTime.now()));
    }

    public void exit() {
        STATE.set(null);
    }

    public static boolean active() {
        return STATE.get() != null;
    }

    public static State state() {
        return STATE.get();
    }
}
