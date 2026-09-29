package com.erp.module.production.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.module.production.controller.vo.WorkOrderVOs.Dispatchable;
import com.erp.module.production.controller.vo.WorkOrderVOs.DispatchReq;
import com.erp.module.production.controller.vo.WorkOrderVOs.DispatchResult;
import com.erp.module.production.controller.vo.WorkOrderVOs.Load;
import com.erp.module.production.controller.vo.WorkOrderVOs.WorkOrderQuery;
import com.erp.module.production.controller.vo.WorkOrderVOs.WorkOrderRow;
import com.erp.module.production.service.workorder.WorkOrderService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 工单派工（需求 09-02） */
@Tag(name = "生产 - 工单派工")
@RestController
@RequestMapping("/api/production")
public class ProductionWorkOrderController {

    private final WorkOrderService service;

    public ProductionWorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping("/work-orders")
    @PreAuthorize("@ss.has('mfg:work-order:query')")
    public CommonResult<PageResult<WorkOrderRow>> page(@Valid WorkOrderQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/work-orders/dispatchable")
    @PreAuthorize("@ss.hasAny('mfg:work-order:create', 'mfg:work-order:query')")
    public CommonResult<List<Dispatchable>> dispatchable(@RequestParam(required = false) Long deptId, @RequestParam(required = false) Long prodOrderId) {
        return CommonResult.success(service.dispatchable(deptId, prodOrderId));
    }

    @GetMapping("/work-orders/load")
    @PreAuthorize("@ss.hasAny('mfg:work-order:create', 'mfg:work-order:query')")
    public CommonResult<Load> load(@RequestParam Long workCenterId, @RequestParam LocalDate date) {
        return CommonResult.success(service.load(workCenterId, date));
    }

    @PostMapping("/work-orders/batch")
    @PreAuthorize("@ss.has('mfg:work-order:create')")
    public CommonResult<DispatchResult> dispatch(@Valid @RequestBody DispatchReq req) {
        return CommonResult.success(service.dispatch(req));
    }

    @PostMapping("/work-orders/{id}/cancel")
    @PreAuthorize("@ss.has('mfg:work-order:update')")
    public CommonResult<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return CommonResult.success();
    }

    @PostMapping("/work-orders/{id}/complete")
    @PreAuthorize("@ss.has('mfg:work-order:update')")
    public CommonResult<Void> complete(@PathVariable Long id) {
        service.complete(id);
        return CommonResult.success();
    }

    @GetMapping("/work-orders/{id}/print-data")
    @PreAuthorize("@ss.has('mfg:work-order:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
