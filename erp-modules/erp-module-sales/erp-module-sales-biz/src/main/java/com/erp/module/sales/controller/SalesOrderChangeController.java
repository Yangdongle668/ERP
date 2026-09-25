package com.erp.module.sales.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
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
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeDetail;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeQuery;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeRow;
import com.erp.module.sales.controller.vo.OrderChangeVOs.ChangeSave;
import com.erp.module.sales.controller.vo.OrderVOs.SubmitReq;
import com.erp.module.sales.service.order.OrderChangeService;

/** 销售订单变更（需求 04-04） */
@Tag(name = "销售 - 订单变更")
@RestController
@RequestMapping("/api/sales")
public class SalesOrderChangeController {

    private final OrderChangeService service;

    public SalesOrderChangeController(OrderChangeService service) {
        this.service = service;
    }

    @GetMapping("/order-changes")
    @PreAuthorize("@ss.hasAny('sales:order:change', 'sales:order:query')")
    public CommonResult<PageResult<ChangeRow>> page(@Valid ChangeQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/order-changes/{id}")
    @PreAuthorize("@ss.hasAny('sales:order:change', 'sales:order:query')")
    public CommonResult<ChangeDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    /** 从订单新建变更单（带入订单当前内容） */
    @PostMapping("/order-changes")
    @PreAuthorize("@ss.has('sales:order:change')")
    public CommonResult<Long> create(@RequestParam Long orderId) {
        return CommonResult.success(service.create(orderId));
    }

    @PutMapping("/order-changes/{id}")
    @PreAuthorize("@ss.has('sales:order:change')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ChangeSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/order-changes/{id}")
    @PreAuthorize("@ss.has('sales:order:change')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/order-changes/{id}/submit")
    @PreAuthorize("@ss.has('sales:order:change')")
    public CommonResult<DocResult> submit(@PathVariable Long id, @RequestBody(required = false) SubmitReq req) {
        return CommonResult.success(service.submit(id, req != null && Boolean.TRUE.equals(req.confirmCredit())));
    }

    @PostMapping("/order-changes/{id}/void")
    @PreAuthorize("@ss.has('sales:order:change')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }
}
