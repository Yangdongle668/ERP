package com.erp.module.system.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.task.AsyncTaskApi;
import com.erp.module.system.api.task.TaskContext;
import com.erp.module.system.api.task.TaskRunner;
import com.erp.module.system.dal.dataobject.AsyncTaskDO;
import com.erp.module.system.dal.mapper.AsyncTaskMapper;
import com.erp.module.system.service.support.NodeId;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务中心（需求 01-系统管理/12 第 2 节）。
 *
 * <p>提交时写入 WAITING 记录并排队；调度器按“全系统并发 ≤ 线程池大小、同一用户运行中 ≤ 3”（R01）取出执行。
 * 任务以提交人的身份在后台线程执行（安全上下文、数据范围、追踪号随任务传递）。执行体只保存在本实例内存中，
 * 实例重启时本实例未完成的任务标记为失败（R02）。
 */
@Slf4j
@Service
public class AsyncTaskService implements AsyncTaskApi {

    public static final String FILE_BIZ_TYPE = "SYS_TASK";
    static final int PER_USER_RUNNING = 3;
    static final int RESULT_KEEP_DAYS = 7;
    private static final int MESSAGE_MAX = 1000;

    private final AsyncTaskMapper taskMapper;
    private final FileApi fileApi;
    private final NotifyApi notifyApi;
    private final NodeId node;
    private final int poolSize;
    private final ExecutorService executor;

    /** 排队中的任务（按提交顺序） */
    private final LinkedHashMap<Long, Pending> waiting = new LinkedHashMap<>();
    /** 运行中：任务 ID → 提交人 */
    private final Map<Long, Long> running = new HashMap<>();

    private record Pending(Long taskId, Long userId, TaskRunner runner, Authentication auth, Map<String, String> mdc) {
    }

    public AsyncTaskService(AsyncTaskMapper taskMapper, FileApi fileApi, NotifyApi notifyApi, NodeId node,
                            @Value("${erp.task.pool-size:4}") int poolSize) {
        this.taskMapper = taskMapper;
        this.fileApi = fileApi;
        this.notifyApi = notifyApi;
        this.node = node;
        this.poolSize = poolSize;
        AtomicInteger seq = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "erp-task-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** R02：本实例重启前未完成的任务标记为失败 */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterrupted() {
        int n = taskMapper.update(null, new LambdaUpdateWrapper<AsyncTaskDO>()
                .in(AsyncTaskDO::getStatus, AsyncTaskDO.WAITING, AsyncTaskDO.RUNNING).eq(AsyncTaskDO::getNode, node.value())
                .set(AsyncTaskDO::getStatus, AsyncTaskDO.FAILED)
                .set(AsyncTaskDO::getErrorMessage, SystemErrorCodes.TASK_INTERRUPTED.message())
                .set(AsyncTaskDO::getFinishedAt, LocalDateTime.now()).set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        if (n > 0) log.warn("[任务中心] {} 个任务因服务重启中断", n);
    }

    // ==================== 提交与调度 ====================

    @Override
    public Long submit(String taskType, String name, String moduleCode, TaskRunner runner) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        AsyncTaskDO t = new AsyncTaskDO();
        t.setTaskType(taskType);
        t.setName(name);
        t.setModuleCode(moduleCode);
        t.setStatus(AsyncTaskDO.WAITING);
        t.setProgress(0);
        t.setResultExpired(false);
        t.setSubmittedBy(user == null ? null : user.id());
        t.setNode(node.value());
        taskMapper.insert(t);
        Pending p = new Pending(t.getId(), t.getSubmittedBy(), runner, SecurityContextHolder.getContext().getAuthentication(),
                MDC.getCopyOfContextMap());
        // 调用方事务提交后才开始执行（避免任务读不到调用方尚未提交的数据）
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) enqueue(p);
                    else finish(p.taskId(), AsyncTaskDO.CANCELED, null, "提交任务的操作已回滚");
                }
            });
        } else {
            enqueue(p);
        }
        return t.getId();
    }

    private void enqueue(Pending p) {
        synchronized (this) {
            waiting.put(p.taskId(), p);
        }
        dispatch();
    }

    /** 从队列取出可执行的任务（R01） */
    private void dispatch() {
        List<Pending> toStart = new java.util.ArrayList<>();
        synchronized (this) {
            Iterator<Pending> it = waiting.values().iterator();
            while (it.hasNext() && running.size() < poolSize) {
                Pending p = it.next();
                long mine = running.values().stream().filter(u -> u != null && u.equals(p.userId())).count();
                if (p.userId() != null && mine >= PER_USER_RUNNING) continue;
                it.remove();
                running.put(p.taskId(), p.userId());
                toStart.add(p);
            }
        }
        for (Pending p : toStart) {
            taskMapper.update(null, new LambdaUpdateWrapper<AsyncTaskDO>().eq(AsyncTaskDO::getId, p.taskId())
                    .set(AsyncTaskDO::getStatus, AsyncTaskDO.RUNNING).set(AsyncTaskDO::getStartedAt, LocalDateTime.now())
                    .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
            executor.execute(() -> execute(p));
        }
    }

    private void execute(Pending p) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(p.auth());
        SecurityContextHolder.setContext(ctx);
        if (p.mdc() != null) MDC.setContextMap(p.mdc());
        Context c = new Context(p.taskId());
        try {
            p.runner().run(c);
            finish(p.taskId(), AsyncTaskDO.SUCCESS, c.message, null);
        } catch (Throwable e) {
            String msg = e instanceof BizException ? e.getMessage() : "执行失败：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : "：" + e.getMessage());
            log.error("[任务中心] 任务 {} 执行失败", p.taskId(), e);
            finish(p.taskId(), AsyncTaskDO.FAILED, c.message, msg);
        } finally {
            SecurityContextHolder.clearContext();
            MDC.clear();
            synchronized (this) {
                running.remove(p.taskId());
            }
            dispatch();
        }
    }

    private void finish(Long taskId, String status, String message, String error) {
        LocalDateTime now = LocalDateTime.now();
        // 只结束仍在排队或运行中的任务（已被标记为中断的不再覆盖）
        LambdaUpdateWrapper<AsyncTaskDO> w = new LambdaUpdateWrapper<AsyncTaskDO>().eq(AsyncTaskDO::getId, taskId)
                .in(AsyncTaskDO::getStatus, AsyncTaskDO.WAITING, AsyncTaskDO.RUNNING)
                .set(AsyncTaskDO::getStatus, status).set(AsyncTaskDO::getFinishedAt, now).set(AsyncTaskDO::getUpdatedAt, now)
                .set(AsyncTaskDO::getResultMessage, truncate(message)).set(AsyncTaskDO::getErrorMessage, truncate(error));
        if (AsyncTaskDO.SUCCESS.equals(status)) w.set(AsyncTaskDO::getProgress, 100);
        if (taskMapper.update(null, w) == 0) return;
        AsyncTaskDO t = taskMapper.selectById(taskId);
        if (t != null && t.getSubmittedBy() != null && !AsyncTaskDO.CANCELED.equals(status)) {
            boolean ok = AsyncTaskDO.SUCCESS.equals(status);
            try {
                notifyApi.message(new MessageSendEvent(List.of(t.getSubmittedBy()), MessageSendEvent.Type.TASK_DONE,
                        ok ? "你的任务【" + t.getName() + "】已完成" : "你的任务【" + t.getName() + "】执行失败",
                        ok ? t.getResultMessage() : t.getErrorMessage(), "/system/task", false));
            } catch (RuntimeException e) {
                log.warn("[任务中心] 发送完成消息失败 task={}", taskId, e);
            }
        }
    }

    /** 执行上下文：进度写库节流 500ms */
    private final class Context implements TaskContext {
        private final Long taskId;
        private int lastProgress = -1;
        private long lastWrite;
        private String message;

        Context(Long taskId) {
            this.taskId = taskId;
        }

        @Override
        public Long taskId() {
            return taskId;
        }

        @Override
        public void progress(int percent) {
            int p = Math.max(0, Math.min(99, percent));
            long now = System.currentTimeMillis();
            if (p == lastProgress || now - lastWrite < 500) return;
            lastProgress = p;
            lastWrite = now;
            taskMapper.update(null, new LambdaUpdateWrapper<AsyncTaskDO>().eq(AsyncTaskDO::getId, taskId).set(AsyncTaskDO::getProgress, p));
        }

        @Override
        public void resultFile(String fileName, String contentType, byte[] content) {
            Long fileId = fileApi.saveGenerated(FILE_BIZ_TYPE, taskId, fileName, contentType, content);
            taskMapper.update(null, new LambdaUpdateWrapper<AsyncTaskDO>().eq(AsyncTaskDO::getId, taskId).set(AsyncTaskDO::getResultFileId, fileId));
        }

        @Override
        public void resultMessage(String message) {
            this.message = message;
        }
    }

    // ==================== 查询与取消 ====================

    public record TaskQuery(String taskType, String status, LocalDateTime timeFrom, LocalDateTime timeTo, boolean all) {
    }

    public PageResult<AsyncTaskDO> page(TaskQuery q, int pageNo, int pageSize) {
        LoginUser me = SecurityUtils.getLoginUser();
        boolean all = q.all() && canSeeAll(me);
        Page<AsyncTaskDO> page = taskMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<AsyncTaskDO>()
                .eq(!all, AsyncTaskDO::getSubmittedBy, me.id())
                .eq(StringUtils.hasText(q.taskType()), AsyncTaskDO::getTaskType, q.taskType())
                .eq(StringUtils.hasText(q.status()), AsyncTaskDO::getStatus, q.status())
                .ge(q.timeFrom() != null, AsyncTaskDO::getCreatedAt, q.timeFrom())
                .le(q.timeTo() != null, AsyncTaskDO::getCreatedAt, q.timeTo())
                .orderByDesc(AsyncTaskDO::getCreatedAt).orderByDesc(AsyncTaskDO::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public AsyncTaskDO get(Long id) {
        AsyncTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || !canView(t)) throw BizException.of(SystemErrorCodes.TASK_NOT_EXISTS);
        return t;
    }

    public void cancel(Long id) {
        AsyncTaskDO t = get(id);
        boolean removed;
        synchronized (this) {
            removed = waiting.remove(id) != null;
        }
        if (!removed || !AsyncTaskDO.WAITING.equals(t.getStatus())) throw BizException.of(SystemErrorCodes.TASK_CANNOT_CANCEL);
        finish(id, AsyncTaskDO.CANCELED, null, "已取消");
    }

    /** 提交人或有 system:task:all 权限的用户可见 */
    static boolean canView(AsyncTaskDO t) {
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        return me != null && (me.id().equals(t.getSubmittedBy()) || canSeeAll(me));
    }

    private static boolean canSeeAll(LoginUser me) {
        return me.permissions().contains(LoginUser.ALL_PERMISSION) || me.permissions().contains("system:task:all");
    }

    /** R03：结果文件保留 7 天 */
    public int cleanupExpiredResults() {
        List<AsyncTaskDO> expired = taskMapper.selectList(new LambdaQueryWrapper<AsyncTaskDO>().isNotNull(AsyncTaskDO::getResultFileId)
                .eq(AsyncTaskDO::getResultExpired, false).lt(AsyncTaskDO::getFinishedAt, LocalDateTime.now().minusDays(RESULT_KEEP_DAYS)));
        for (AsyncTaskDO t : expired) {
            fileApi.deleteByBiz(FILE_BIZ_TYPE, t.getId());
            taskMapper.update(null, new LambdaUpdateWrapper<AsyncTaskDO>().eq(AsyncTaskDO::getId, t.getId())
                    .set(AsyncTaskDO::getResultExpired, true).set(AsyncTaskDO::getResultFileId, null));
        }
        return expired.size();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MESSAGE_MAX ? s : s.substring(0, MESSAGE_MAX);
    }
}
