package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectDetail;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectQuery;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectRow;
import com.erp.module.engineering.controller.vo.ProjectVOs.ProjectSave;
import com.erp.module.engineering.controller.vo.ProjectVOs.ReasonReq;
import com.erp.module.engineering.controller.vo.ProjectVOs.StageReq;
import com.erp.module.engineering.controller.vo.ProjectVOs.TaskSave;
import com.erp.module.engineering.controller.vo.ProjectVOs.TaskStatusReq;
import com.erp.module.engineering.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 研发项目（需求 05-06 第 5 节）。编辑项目、新建任务：项目经理或 eng:project:update（在服务中校验，R06）；
 * 更新任务状态：任务负责人或项目经理。
 */
@Tag(name = "研发工程 - 研发项目")
@RestController
@RequestMapping("/api/engineering/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<PageResult<ProjectRow>> page(@Valid ProjectQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<ProjectDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:project:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProjectSave req) {
        return CommonResult.success(service.create(req));
    }

    @Operation(summary = "编辑项目：项目经理或有 eng:project:update")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:project:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> stage(@PathVariable Long id, @Valid @RequestBody StageReq req) {
        service.advanceStage(id, req.stage());
        return CommonResult.success();
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> hold(@PathVariable Long id) {
        service.hold(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> resume(@PathVariable Long id) {
        service.resume(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@ss.has('eng:project:close')")
    public CommonResult<Void> complete(@PathVariable Long id) {
        service.complete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.has('eng:project:close')")
    public CommonResult<Void> cancel(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        service.cancel(id, req.reason());
        return CommonResult.success();
    }

    // ==================== 任务 ====================

    @PostMapping("/{id}/tasks")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Long> createTask(@PathVariable Long id, @Valid @RequestBody TaskSave req) {
        return CommonResult.success(service.createTask(id, req));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> updateTask(@PathVariable Long id, @PathVariable Long taskId, @Valid @RequestBody TaskSave req) {
        service.updateTask(id, taskId, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> deleteTask(@PathVariable Long id, @PathVariable Long taskId) {
        service.deleteTask(id, taskId);
        return CommonResult.success();
    }

    @PostMapping("/{id}/tasks/{taskId}/status")
    @PreAuthorize("@ss.has('eng:project:query')")
    public CommonResult<Void> taskStatus(@PathVariable Long id, @PathVariable Long taskId, @Valid @RequestBody TaskStatusReq req) {
        service.updateTaskStatus(id, taskId, req.status(), req.deliverable());
        return CommonResult.success();
    }
}
