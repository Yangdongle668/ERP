package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.controller.vo.JobVOs.TaskResp;
import com.erp.module.system.dal.dataobject.AsyncTaskDO;
import com.erp.module.system.service.task.AsyncTaskService;
import com.erp.module.system.service.task.AsyncTaskService.TaskQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 任务中心（需求 01-系统管理/12 第 2 节）：所有登录用户可访问，只能看到自己提交的任务；
 * 有 system:task:all 权限时可查看全部（all=true）。结果文件通过 /system/files/{id}/download 下载。
 */
@Tag(name = "系统管理 - 任务中心")
@RestController
@RequestMapping("/api/system/tasks")
public class TaskController {

    private final AsyncTaskService taskService;
    private final UserApi userApi;

    public TaskController(AsyncTaskService taskService, UserApi userApi) {
        this.taskService = taskService;
        this.userApi = userApi;
    }

    @GetMapping
    public CommonResult<PageResult<TaskResp>> page(@RequestParam(required = false) String taskType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime timeFrom,
                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime timeTo,
                                                   @RequestParam(defaultValue = "false") boolean all,
                                                   @RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<AsyncTaskDO> page = taskService.page(new TaskQuery(taskType, status, timeFrom, timeTo, all), pageNo, Math.min(pageSize, 200));
        return CommonResult.success(new PageResult<>(toResp(page.list()), page.total()));
    }

    @GetMapping("/{id}")
    public CommonResult<TaskResp> get(@PathVariable Long id) {
        return CommonResult.success(toResp(List.of(taskService.get(id))).get(0));
    }

    @PostMapping("/{id}/cancel")
    public CommonResult<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return CommonResult.success(null);
    }

    private List<TaskResp> toResp(List<AsyncTaskDO> tasks) {
        Map<Long, UserDTO> users = userApi.list(tasks.stream().map(AsyncTaskDO::getSubmittedBy).filter(Objects::nonNull).distinct().toList());
        return tasks.stream().map(t -> new TaskResp(t.getId(), t.getTaskType(), t.getName(), t.getModuleCode(), t.getStatus(),
                t.getProgress() == null ? 0 : t.getProgress(), t.getResultFileId(), Boolean.TRUE.equals(t.getResultExpired()), t.getResultMessage(),
                t.getErrorMessage(), t.getSubmittedBy(), users.containsKey(t.getSubmittedBy()) ? users.get(t.getSubmittedBy()).realName() : null,
                t.getCreatedAt(), t.getStartedAt(), t.getFinishedAt())).toList();
    }
}
