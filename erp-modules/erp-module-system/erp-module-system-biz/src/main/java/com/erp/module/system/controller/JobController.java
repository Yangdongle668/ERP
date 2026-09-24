package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.module.ErpModule;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.controller.vo.JobVOs.CronReq;
import com.erp.module.system.controller.vo.JobVOs.JobLogResp;
import com.erp.module.system.controller.vo.JobVOs.JobResp;
import com.erp.module.system.dal.dataobject.JobDO;
import com.erp.module.system.dal.dataobject.JobLogDO;
import com.erp.module.system.service.job.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 定时任务（需求 01-系统管理/12 第 3 节） */
@Tag(name = "系统管理 - 定时任务")
@RestController
@RequestMapping("/api/system/jobs")
public class JobController {

    private final JobService jobService;
    private final UserApi userApi;
    private final Map<String, String> moduleNames;

    public JobController(JobService jobService, UserApi userApi, List<ErpModule> modules) {
        this.jobService = jobService;
        this.userApi = userApi;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
    }

    @GetMapping
    @PreAuthorize("@ss.has('system:job:query')")
    public CommonResult<List<JobResp>> list() {
        LocalDateTime now = LocalDateTime.now();
        return CommonResult.success(jobService.list().stream().map(j -> toResp(j, now)).toList());
    }

    /** 最近 5 次执行时间预览（编辑 Cron 时实时调用） */
    @GetMapping("/cron-preview")
    @PreAuthorize("@ss.has('system:job:query')")
    public CommonResult<List<LocalDateTime>> preview(@RequestParam String cron) {
        return CommonResult.success(jobService.preview(cron, 5));
    }

    @OperLog("修改定时任务 Cron")
    @PutMapping("/{code}/cron")
    @PreAuthorize("@ss.has('system:job:update')")
    public CommonResult<Void> updateCron(@PathVariable String code, @Valid @RequestBody CronReq req) {
        jobService.updateCron(code, req.cron());
        return CommonResult.success(null);
    }

    @OperLog("恢复定时任务默认 Cron")
    @PostMapping("/{code}/reset-cron")
    @PreAuthorize("@ss.has('system:job:update')")
    public CommonResult<Void> resetCron(@PathVariable String code) {
        jobService.resetCron(code);
        return CommonResult.success(null);
    }

    @OperLog("启用定时任务")
    @PostMapping("/{code}/enable")
    @PreAuthorize("@ss.has('system:job:update')")
    public CommonResult<Void> enable(@PathVariable String code) {
        jobService.setEnabled(code, true);
        return CommonResult.success(null);
    }

    @OperLog("停用定时任务")
    @PostMapping("/{code}/disable")
    @PreAuthorize("@ss.has('system:job:update')")
    public CommonResult<Void> disable(@PathVariable String code) {
        jobService.setEnabled(code, false);
        return CommonResult.success(null);
    }

    @OperLog("立即执行定时任务")
    @PostMapping("/{code}/run")
    @PreAuthorize("@ss.has('system:job:update')")
    public CommonResult<Void> run(@PathVariable String code) {
        jobService.runNow(code);
        return CommonResult.success(null);
    }

    @GetMapping("/{code}/logs")
    @PreAuthorize("@ss.has('system:job:query')")
    public CommonResult<PageResult<JobLogResp>> logs(@PathVariable String code, @RequestParam(defaultValue = "1") int pageNo,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<JobLogDO> page = jobService.logs(code, pageNo, Math.min(pageSize, 200));
        Map<Long, UserDTO> users = userApi.list(page.list().stream().map(JobLogDO::getOperatorId).filter(Objects::nonNull).distinct().toList());
        return CommonResult.success(new PageResult<>(page.list().stream().map(l -> new JobLogResp(l.getId(), l.getJobCode(), l.getStartedAt(),
                l.getFinishedAt(), l.getDurationMs(), l.getResult(), l.getMessage(), l.getTriggerType(),
                l.getOperatorId() == null ? null : users.containsKey(l.getOperatorId()) ? users.get(l.getOperatorId()).realName() : null)).toList(),
                page.total()));
    }

    private JobResp toResp(JobDO j, LocalDateTime now) {
        boolean running = j.getLockedUntil() != null && j.getLockedUntil().isAfter(now);
        return new JobResp(j.getCode(), j.getName(), j.getModuleCode(), moduleNames.getOrDefault(j.getModuleCode(), j.getModuleCode()), j.getCron(),
                j.getDefaultCron(), Boolean.TRUE.equals(j.getEnabled()), j.getLastRunAt(), j.getLastResult(), j.getLastMessage(), j.getNextRunAt(), running);
    }
}
