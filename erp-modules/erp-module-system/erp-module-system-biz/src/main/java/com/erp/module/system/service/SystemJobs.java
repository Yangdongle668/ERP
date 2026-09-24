package com.erp.module.system.service;

import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.service.file.FileService;
import com.erp.module.system.service.job.JobService;
import com.erp.module.system.service.task.AsyncTaskService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 系统管理模块的定时任务（需求 01-12 第 3 节）。 */
@Component
public class SystemJobs {

    private final LogService logService;
    private final JobService jobService;
    private final FileService fileService;
    private final AsyncTaskService taskService;
    private final CurrencyService currencyService;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;

    public SystemJobs(LogService logService, JobService jobService, FileService fileService, AsyncTaskService taskService,
                      CurrencyService currencyService, ParamApi paramApi, NotifyApi notifyApi) {
        this.logService = logService;
        this.jobService = jobService;
        this.fileService = fileService;
        this.taskService = taskService;
        this.currencyService = currencyService;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
    }

    /** 01-11 LOG-R01：清理超过保留期的操作日志、登录日志；定时任务执行日志保留 90 天 */
    @ErpJob(code = "SYS_LOG_CLEANUP", name = "日志清理", cron = "0 0 2 * * ?")
    public String cleanupLogs() {
        return logService.cleanup() + "、任务执行日志 " + jobService.cleanupLogs() + " 条";
    }

    /** 01-12 SYS-FIL-R03/R05：未绑定超过 24 小时、删除超过 30 天的附件 */
    @ErpJob(code = "SYS_FILE_CLEANUP", name = "附件清理", cron = "0 30 3 * * ?")
    public String cleanupFiles() {
        return "清理附件 " + fileService.cleanup() + " 个";
    }

    /** 01-12 SYS-TSK-R03：后台任务结果文件保留 7 天 */
    @ErpJob(code = "SYS_TASK_CLEANUP", name = "后台任务结果清理", cron = "0 40 3 * * ?")
    public String cleanupTaskResults() {
        return "过期结果文件 " + taskService.cleanupExpiredResults() + " 个";
    }

    /** 01-07：每个工作日 10:00 检查当天启用外币是否已维护日汇率，未维护时预警给汇率维护人员 */
    @ErpJob(code = "SYS_RATE_REMIND", name = "汇率未维护提醒", cron = "0 0 10 ? * MON-FRI")
    public String remindRates() {
        if (!paramApi.getBool("sys.rate.remind-enabled")) return "提醒已关闭（参数 sys.rate.remind-enabled）";
        LocalDate today = LocalDate.now();
        List<String> missing = currencyService.missingDailyRates(today);
        String key = "SYS_RATE_MISSING";
        if (missing.isEmpty()) {
            notifyApi.resolve(key);
            return "今日汇率已全部维护";
        }
        notifyApi.alert(new AlertRaisedEvent(key, "RATE_MISSING", AlertRaisedEvent.Level.WARNING, null, "system:rate:create",
                null, null, "今日汇率未维护", today + " 以下币别尚未维护日汇率：" + String.join("、", missing), "/system/currency"));
        return "未维护：" + String.join("、", missing);
    }
}
