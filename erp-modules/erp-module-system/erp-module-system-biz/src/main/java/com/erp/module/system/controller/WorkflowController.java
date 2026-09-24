package com.erp.module.system.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.operlog.OperLog;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.controller.vo.WorkflowVOs.BatchApproveReq;
import com.erp.module.system.controller.vo.WorkflowVOs.BatchResult;
import com.erp.module.system.controller.vo.WorkflowVOs.BizTypeResp;
import com.erp.module.system.controller.vo.WorkflowVOs.ByBizResp;
import com.erp.module.system.controller.vo.WorkflowVOs.CommentReq;
import com.erp.module.system.controller.vo.WorkflowVOs.DefinitionSave;
import com.erp.module.system.controller.vo.WorkflowVOs.DefinitionView;
import com.erp.module.system.controller.vo.WorkflowVOs.Definitions;
import com.erp.module.system.controller.vo.WorkflowVOs.EnabledReq;
import com.erp.module.system.controller.vo.WorkflowVOs.HistoryResp;
import com.erp.module.system.controller.vo.WorkflowVOs.InstanceQuery;
import com.erp.module.system.controller.vo.WorkflowVOs.InstanceView;
import com.erp.module.system.controller.vo.WorkflowVOs.MonitorResp;
import com.erp.module.system.controller.vo.WorkflowVOs.MyInstanceResp;
import com.erp.module.system.controller.vo.WorkflowVOs.MyTaskResp;
import com.erp.module.system.controller.vo.WorkflowVOs.PublishReq;
import com.erp.module.system.controller.vo.WorkflowVOs.ReasonReq;
import com.erp.module.system.controller.vo.WorkflowVOs.TransferReq;
import com.erp.module.system.service.workflow.WfDefinitionService;
import com.erp.module.system.service.workflow.WfQueryService;
import com.erp.module.system.service.workflow.WorkflowEngine;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审批流（需求 01-系统管理/08 第 6 节） */
@Tag(name = "系统管理 - 审批流")
@RestController
@RequestMapping("/api/system/workflow")
public class WorkflowController {

    private static final String MONITOR = "system:workflow:monitor";

    private final WfDefinitionService definitionService;
    private final WorkflowEngine engine;
    private final WfQueryService queryService;

    public WorkflowController(WfDefinitionService definitionService, WorkflowEngine engine, WfQueryService queryService) {
        this.definitionService = definitionService;
        this.engine = engine;
        this.queryService = queryService;
    }

    // ==================== 配置 ====================

    @GetMapping("/biz-types")
    @PreAuthorize("@ss.has('system:workflow:query')")
    public CommonResult<List<BizTypeResp>> bizTypes() {
        return CommonResult.success(definitionService.bizTypes());
    }

    @GetMapping("/definitions")
    @PreAuthorize("@ss.has('system:workflow:query')")
    public CommonResult<Definitions> definitions(@RequestParam String bizType) {
        return CommonResult.success(definitionService.definitions(bizType));
    }

    @GetMapping("/definitions/history")
    @PreAuthorize("@ss.has('system:workflow:query')")
    public CommonResult<List<HistoryResp>> history(@RequestParam String bizType) {
        return CommonResult.success(definitionService.history(bizType));
    }

    @GetMapping("/definitions/{id}")
    @PreAuthorize("@ss.has('system:workflow:query')")
    public CommonResult<DefinitionView> definition(@PathVariable Long id) {
        return CommonResult.success(definitionService.get(id));
    }

    @OperLog("编辑审批流程")
    @PostMapping("/definitions/draft")
    @PreAuthorize("@ss.has('system:workflow:update')")
    public CommonResult<DefinitionView> draft(@RequestParam String bizType) {
        return CommonResult.success(definitionService.draft(bizType));
    }

    @OperLog("保存审批流程草稿")
    @PutMapping("/definitions/{id}")
    @PreAuthorize("@ss.has('system:workflow:update')")
    public CommonResult<DefinitionView> saveDraft(@PathVariable Long id, @Valid @RequestBody DefinitionSave req) {
        return CommonResult.success(definitionService.saveDraft(id, req));
    }

    @OperLog("放弃审批流程草稿")
    @DeleteMapping("/definitions/{id}")
    @PreAuthorize("@ss.has('system:workflow:update')")
    public CommonResult<Void> discard(@PathVariable Long id) {
        definitionService.discardDraft(id);
        return CommonResult.success(null);
    }

    @OperLog("发布审批流程")
    @PostMapping("/definitions/{id}/publish")
    @PreAuthorize("@ss.has('system:workflow:update')")
    public CommonResult<Void> publish(@PathVariable Long id, @Valid @RequestBody(required = false) PublishReq req) {
        definitionService.publish(id, req == null ? null : req.remark());
        return CommonResult.success(null);
    }

    @OperLog("启用/关闭审批")
    @PostMapping("/biz-types/{bizType}/enabled")
    @PreAuthorize("@ss.has('system:workflow:update')")
    public CommonResult<Void> enabled(@PathVariable String bizType, @RequestBody EnabledReq req) {
        definitionService.setEnabled(bizType, req.enabled());
        return CommonResult.success(null);
    }

    // ==================== 审批（登录即可，由引擎校验处理人） ====================

    /** 单据的审批记录；只返回审批记录（单据查看权限由业务模块的详情接口控制） */
    @GetMapping("/instances/by-biz")
    public CommonResult<ByBizResp> byBiz(@RequestParam String bizType, @RequestParam Long bizId) {
        return CommonResult.success(queryService.byBiz(bizType, bizId, SecurityUtils.getLoginUser().id()));
    }

    @OperLog("审批通过")
    @PostMapping("/tasks/{taskId}/approve")
    public CommonResult<Void> approve(@PathVariable Long taskId, @Valid @RequestBody(required = false) CommentReq req) {
        engine.approve(taskId, req == null ? null : req.comment(), SecurityUtils.getLoginUser().id());
        return CommonResult.success(null);
    }

    @OperLog("审批驳回")
    @PostMapping("/tasks/{taskId}/reject")
    public CommonResult<Void> reject(@PathVariable Long taskId, @Valid @RequestBody(required = false) CommentReq req) {
        engine.reject(taskId, req == null ? null : req.comment(), SecurityUtils.getLoginUser().id());
        return CommonResult.success(null);
    }

    @OperLog("审批转交")
    @PostMapping("/tasks/{taskId}/transfer")
    public CommonResult<Void> transfer(@PathVariable Long taskId, @Valid @RequestBody TransferReq req) {
        LoginUser me = SecurityUtils.getLoginUser();
        boolean monitor = me.permissions().contains(LoginUser.ALL_PERMISSION) || me.permissions().contains(MONITOR);
        engine.transfer(taskId, req.toUserId(), req.comment(), me.id(), monitor);
        return CommonResult.success(null);
    }

    @OperLog("撤回审批")
    @PostMapping("/instances/{id}/withdraw")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        engine.withdrawInstance(id, SecurityUtils.getLoginUser().id());
        return CommonResult.success(null);
    }

    @OperLog("批量审批通过")
    @PostMapping("/tasks/batch-approve")
    public CommonResult<List<BatchResult>> batchApprove(@Valid @RequestBody BatchApproveReq req) {
        return CommonResult.success(queryService.batchApprove(req.taskIds(), req.comment(), SecurityUtils.getLoginUser().id()));
    }

    @GetMapping("/tasks/my")
    public CommonResult<PageResult<MyTaskResp>> myTasks(@RequestParam(defaultValue = "PENDING") String status,
                                                        @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(queryService.myTasks(SecurityUtils.getLoginUser().id(), status, pageNo, Math.min(pageSize, 200)));
    }

    @GetMapping("/instances/my")
    public CommonResult<PageResult<MyInstanceResp>> myInstances(@RequestParam(required = false) String status,
                                                                @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.success(queryService.myInstances(SecurityUtils.getLoginUser().id(), status, pageNo, Math.min(pageSize, 200)));
    }

    // ==================== 监控 ====================

    @GetMapping("/instances")
    @PreAuthorize("@ss.has('system:workflow:monitor')")
    public CommonResult<PageResult<MonitorResp>> instances(@Valid InstanceQuery q) {
        return CommonResult.success(queryService.monitor(q));
    }

    @GetMapping("/instances/{id}")
    @PreAuthorize("@ss.has('system:workflow:monitor')")
    public CommonResult<InstanceView> instance(@PathVariable Long id) {
        return CommonResult.success(queryService.instance(id));
    }

    @OperLog("终止审批")
    @PostMapping("/instances/{id}/terminate")
    @PreAuthorize("@ss.has('system:workflow:monitor')")
    public CommonResult<Void> terminate(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        engine.terminate(id, req.reason(), SecurityUtils.getLoginUser().id());
        return CommonResult.success(null);
    }
}
