package com.erp.module.system.service.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.dal.dataobject.JobDO;
import com.erp.module.system.dal.dataobject.JobLogDO;
import com.erp.module.system.dal.mapper.JobLogMapper;
import com.erp.module.system.dal.mapper.JobMapper;
import com.erp.module.system.service.job.ErpJobCollector.JobHandle;
import com.erp.module.system.service.support.NodeId;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * 定时任务（需求 01-系统管理/12 第 3 节）：启动时把 @ErpJob 声明同步到 sys_job 并按 Cron 调度；
 * 执行前用数据库锁保证多实例只执行一次（R02）；每次执行写 sys_job_log。
 */
@Slf4j
@Service
public class JobService {

    public static final String TRIGGER_SCHEDULE = "SCHEDULE";
    public static final String TRIGGER_MANUAL = "MANUAL";
    private static final int MESSAGE_MAX = 1000;

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final ErpJobCollector collector;
    private final ApplicationContext context;
    private final NodeId node;
    private final Duration lockDuration;
    private final ThreadPoolTaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public JobService(JobMapper jobMapper, JobLogMapper jobLogMapper, ErpJobCollector collector, ApplicationContext context, NodeId node,
                      @Value("${erp.job.lock-minutes:120}") int lockMinutes, @Value("${erp.job.pool-size:4}") int poolSize) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.collector = collector;
        this.context = context;
        this.node = node;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(poolSize);
        this.scheduler.setThreadNamePrefix("erp-job-");
        this.scheduler.setWaitForTasksToCompleteOnShutdown(false);
        this.scheduler.initialize();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

    // ==================== 启动同步 ====================

    @EventListener(ApplicationReadyEvent.class)
    public void syncAndSchedule() {
        Map<String, JobDO> existing = jobMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(JobDO::getCode, j -> j));
        Set<String> declared = collector.handles().stream().map(JobHandle::code).collect(Collectors.toSet());
        for (JobHandle h : collector.handles()) {
            if (!CronExpression.isValidExpression(h.cron())) throw new IllegalStateException("定时任务 " + h.code() + " 的 Cron 不正确: " + h.cron());
            JobDO j = existing.get(h.code());
            if (j == null) {
                j = new JobDO();
                j.setCode(h.code());
                j.setName(h.name());
                j.setModuleCode(h.moduleCode());
                j.setCron(h.cron());
                j.setDefaultCron(h.cron());
                j.setEnabled(true);
                j.setActive(true);
                j.setNextRunAt(next(h.cron()));
                jobMapper.insert(j);
            } else {
                // 管理员没改过 Cron（仍等于旧默认值）时跟随新的默认值
                if (j.getCron().equals(j.getDefaultCron())) j.setCron(h.cron());
                j.setName(h.name());
                j.setModuleCode(h.moduleCode());
                j.setDefaultCron(h.cron());
                j.setActive(true);
                j.setNextRunAt(Boolean.TRUE.equals(j.getEnabled()) ? next(j.getCron()) : null);
                jobMapper.updateByIdOrFail(j);
            }
            schedule(j);
        }
        for (JobDO j : existing.values()) {
            if (!declared.contains(j.getCode()) && Boolean.TRUE.equals(j.getActive())) {
                j.setActive(false);
                j.setNextRunAt(null);
                jobMapper.updateByIdOrFail(j);
            }
        }
        log.info("[定时任务] 已注册 {} 个任务", declared.size());
    }

    private void schedule(JobDO j) {
        ScheduledFuture<?> old = futures.remove(j.getCode());
        if (old != null) old.cancel(false);
        if (Boolean.TRUE.equals(j.getEnabled()) && Boolean.TRUE.equals(j.getActive()) && collector.get(j.getCode()) != null) {
            String code = j.getCode();
            futures.put(code, scheduler.schedule(() -> runScheduled(code), new CronTrigger(j.getCron())));
        }
    }

    // ==================== 执行 ====================

    private void runScheduled(String code) {
        LocalDateTime now = LocalDateTime.now();
        if (jobMapper.tryLock(code, node.value(), now, now.plus(lockDuration)) == 0) {
            log.debug("[定时任务] {} 正在其他实例执行，本次跳过", code);
            return;
        }
        runLocked(code, TRIGGER_SCHEDULE, null);
    }

    /** 立即执行（R03：正在执行时提示）。异步执行，返回后可在执行日志中查看结果。 */
    public void runNow(String code) {
        JobDO j = get(code);
        if (!Boolean.TRUE.equals(j.getActive()) || collector.get(code) == null) throw BizException.of(SystemErrorCodes.JOB_NOT_EXISTS, code);
        LocalDateTime now = LocalDateTime.now();
        if (jobMapper.tryLock(code, node.value(), now, now.plus(lockDuration)) == 0) throw BizException.of(SystemErrorCodes.JOB_RUNNING);
        Long operator = SecurityUtils.getLoginUserIdOrNull();
        scheduler.execute(() -> runLocked(code, TRIGGER_MANUAL, operator));
    }

    /** 同步执行（已持有锁）：写日志、调用方法、释放锁 */
    void runLocked(String code, String trigger, Long operatorId) {
        JobHandle h = collector.get(code);
        JobLogDO logRow = new JobLogDO();
        logRow.setJobCode(code);
        logRow.setStartedAt(LocalDateTime.now());
        logRow.setResult(JobLogDO.RUNNING);
        logRow.setTriggerType(trigger);
        logRow.setOperatorId(operatorId);
        logRow.setNode(node.value());
        jobLogMapper.insert(logRow);
        String result = JobLogDO.SUCCESS;
        String message;
        long start = System.currentTimeMillis();
        try {
            Object bean = context.getBean(h.beanName());
            Method m = AopUtils.selectInvocableMethod(h.method(), bean.getClass());
            m.setAccessible(true);
            Object r = m.invoke(bean);
            message = r == null ? "执行成功" : String.valueOf(r);
        } catch (InvocationTargetException e) {
            result = JobLogDO.FAILED;
            Throwable cause = e.getTargetException();
            message = cause instanceof BizException ? cause.getMessage() : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            log.error("[定时任务] {} 执行失败", code, cause);
        } catch (Exception e) {
            result = JobLogDO.FAILED;
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("[定时任务] {} 执行失败", code, e);
        }
        message = truncate(message);
        LocalDateTime end = LocalDateTime.now();
        logRow.setFinishedAt(end);
        logRow.setDurationMs(System.currentTimeMillis() - start);
        logRow.setResult(result);
        logRow.setMessage(message);
        jobLogMapper.updateById(logRow);
        JobDO j = jobMapper.selectOne(new LambdaQueryWrapper<JobDO>().eq(JobDO::getCode, code));
        jobMapper.finish(code, logRow.getStartedAt(), result, message,
                j != null && Boolean.TRUE.equals(j.getEnabled()) ? next(j.getCron()) : null);
    }

    // ==================== 管理 ====================

    public List<JobDO> list() {
        return jobMapper.selectList(new LambdaQueryWrapper<JobDO>().eq(JobDO::getActive, true)
                .orderByAsc(JobDO::getModuleCode).orderByAsc(JobDO::getCode));
    }

    public void updateCron(String code, String cron) {
        String c = normalize(cron);
        JobDO j = get(code);
        j.setCron(c);
        j.setNextRunAt(Boolean.TRUE.equals(j.getEnabled()) ? next(c) : null);
        jobMapper.updateByIdOrFail(j);
        schedule(j);
    }

    public void resetCron(String code) {
        updateCron(code, get(code).getDefaultCron());
    }

    public void setEnabled(String code, boolean enabled) {
        JobDO j = get(code);
        j.setEnabled(enabled);
        j.setNextRunAt(enabled ? next(j.getCron()) : null);
        jobMapper.updateByIdOrFail(j);
        schedule(j);
    }

    public PageResult<JobLogDO> logs(String code, int pageNo, int pageSize) {
        Page<JobLogDO> page = jobLogMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<JobLogDO>().eq(JobLogDO::getJobCode, code).orderByDesc(JobLogDO::getStartedAt));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /** 最近 n 次执行时间预览（R01 校验） */
    public List<LocalDateTime> preview(String cron, int n) {
        CronExpression expr = CronExpression.parse(normalize(cron));
        List<LocalDateTime> times = new ArrayList<>();
        LocalDateTime t = LocalDateTime.now();
        for (int i = 0; i < n; i++) {
            t = expr.next(t);
            if (t == null) break;
            times.add(t);
        }
        return times;
    }

    /** 清理超过 90 天的执行日志 */
    public int cleanupLogs() {
        return jobLogMapper.delete(new LambdaQueryWrapper<JobLogDO>().lt(JobLogDO::getStartedAt, LocalDateTime.now().minusDays(90)));
    }

    private JobDO get(String code) {
        JobDO j = jobMapper.selectOne(new LambdaQueryWrapper<JobDO>().eq(JobDO::getCode, code));
        if (j == null) throw BizException.of(SystemErrorCodes.JOB_NOT_EXISTS, code);
        return j;
    }

    private static String normalize(String cron) {
        String c = cron == null ? "" : cron.trim().replaceAll("\\s+", " ");
        if (!CronExpression.isValidExpression(c)) throw BizException.of(SystemErrorCodes.JOB_CRON_INVALID);
        return c;
    }

    private static LocalDateTime next(String cron) {
        return CronExpression.isValidExpression(cron) ? CronExpression.parse(cron).next(LocalDateTime.now()) : null;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MESSAGE_MAX ? s : s.substring(0, MESSAGE_MAX);
    }
}
