package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingDetail;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingQuery;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingRow;
import com.erp.module.engineering.controller.vo.RoutingVOs.RoutingSave;
import com.erp.module.engineering.controller.vo.RoutingVOs.SaveResult;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterQuery;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterRow;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterSave;
import com.erp.module.engineering.controller.vo.RoutingVOs.WorkCenterSimple;
import com.erp.module.engineering.service.RoutingService;
import com.erp.module.engineering.service.WorkCenterService;
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

import java.util.List;
import java.util.Map;

/** 工作中心与工艺路线（需求 05-04 第 5 节） */
@Tag(name = "研发工程 - 工作中心与工艺路线")
@RestController
@RequestMapping("/api/engineering")
public class RoutingController {

    private final WorkCenterService workCenterService;
    private final RoutingService routingService;

    public RoutingController(WorkCenterService workCenterService, RoutingService routingService) {
        this.workCenterService = workCenterService;
        this.routingService = routingService;
    }

    // ==================== 工作中心 ====================

    @GetMapping("/work-centers")
    @PreAuthorize("@ss.has('eng:work-center:query')")
    public CommonResult<PageResult<WorkCenterRow>> workCenters(@Valid WorkCenterQuery q) {
        return CommonResult.success(workCenterService.page(q));
    }

    @Operation(summary = "工作中心下拉（登录即可）：启用的工作中心")
    @GetMapping("/work-centers/simple")
    public CommonResult<List<WorkCenterSimple>> simple() {
        return CommonResult.success(workCenterService.simple());
    }

    @PostMapping("/work-centers")
    @PreAuthorize("@ss.has('eng:work-center:create')")
    public CommonResult<Long> createWorkCenter(@Valid @RequestBody WorkCenterSave req) {
        return CommonResult.success(workCenterService.create(req));
    }

    @PutMapping("/work-centers/{id}")
    @PreAuthorize("@ss.has('eng:work-center:update')")
    public CommonResult<Void> updateWorkCenter(@PathVariable Long id, @Valid @RequestBody WorkCenterSave req) {
        workCenterService.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/work-centers/{id}/enable")
    @PreAuthorize("@ss.has('eng:work-center:update')")
    public CommonResult<Void> enableWorkCenter(@PathVariable Long id) {
        workCenterService.setStatus(id, true);
        return CommonResult.success();
    }

    @PostMapping("/work-centers/{id}/disable")
    @PreAuthorize("@ss.has('eng:work-center:update')")
    public CommonResult<Void> disableWorkCenter(@PathVariable Long id) {
        workCenterService.setStatus(id, false);
        return CommonResult.success();
    }

    @DeleteMapping("/work-centers/{id}")
    @PreAuthorize("@ss.has('eng:work-center:delete')")
    public CommonResult<Void> deleteWorkCenter(@PathVariable Long id) {
        workCenterService.delete(id);
        return CommonResult.success();
    }

    // ==================== 工艺路线 ====================

    @GetMapping("/routings")
    @PreAuthorize("@ss.has('eng:routing:query')")
    public CommonResult<PageResult<RoutingRow>> routings(@Valid RoutingQuery q) {
        return CommonResult.success(routingService.page(q));
    }

    @GetMapping("/routings/{id}")
    @PreAuthorize("@ss.has('eng:routing:query')")
    public CommonResult<RoutingDetail> routing(@PathVariable Long id) {
        return CommonResult.success(routingService.detail(id));
    }

    @PostMapping("/routings")
    @PreAuthorize("@ss.has('eng:routing:create')")
    public CommonResult<SaveResult> createRouting(@Valid @RequestBody RoutingSave req) {
        return CommonResult.success(routingService.create(req));
    }

    @PutMapping("/routings/{id}")
    @PreAuthorize("@ss.has('eng:routing:update')")
    public CommonResult<SaveResult> updateRouting(@PathVariable Long id, @Valid @RequestBody RoutingSave req) {
        return CommonResult.success(routingService.update(id, req));
    }

    @DeleteMapping("/routings/{id}")
    @PreAuthorize("@ss.has('eng:routing:delete')")
    public CommonResult<Void> deleteRouting(@PathVariable Long id) {
        routingService.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/routings/{id}/approve")
    @PreAuthorize("@ss.has('eng:routing:approve')")
    public CommonResult<Void> approve(@PathVariable Long id) {
        routingService.approve(id);
        return CommonResult.success();
    }

    @PostMapping("/routings/{id}/unapprove")
    @PreAuthorize("@ss.has('eng:routing:approve')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        routingService.unapprove(id, body == null ? null : body.get("reason"));
        return CommonResult.success();
    }

    @PostMapping("/routings/{id}/set-default")
    @PreAuthorize("@ss.has('eng:routing:set-default')")
    public CommonResult<Void> setDefault(@PathVariable Long id) {
        routingService.setDefault(id);
        return CommonResult.success();
    }

    @PostMapping("/routings/{id}/new-version")
    @PreAuthorize("@ss.has('eng:routing:create')")
    public CommonResult<Long> newVersion(@PathVariable Long id) {
        return CommonResult.success(routingService.newVersion(id));
    }

    @PostMapping("/routings/{id}/disable")
    @PreAuthorize("@ss.has('eng:routing:approve')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        routingService.disable(id);
        return CommonResult.success();
    }
}
