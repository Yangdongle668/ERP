package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.controller.vo.SampleVOs.FeedbackReq;
import com.erp.module.engineering.controller.vo.SampleVOs.ReasonReq;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleDetail;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleQuery;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleRow;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleSave;
import com.erp.module.engineering.controller.vo.SampleVOs.ShipReq;
import com.erp.module.engineering.service.SampleService;
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

import java.util.Map;

/** 样品单（需求 05-07 第 6 节） */
@Tag(name = "研发工程 - 样品")
@RestController
@RequestMapping("/api/engineering/samples")
public class SampleController {

    private final SampleService service;

    public SampleController(SampleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:sample:query')")
    public CommonResult<PageResult<SampleRow>> page(@Valid SampleQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:sample:query')")
    public CommonResult<SampleDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:sample:create')")
    public CommonResult<Long> create(@Valid @RequestBody SampleSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:sample:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody SampleSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:sample:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('eng:sample:submit')")
    public CommonResult<String> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @Operation(summary = "生成样品生产订单（需要生产模块实现 SampleOrderCreator）")
    @PostMapping("/{id}/create-prod-order")
    @PreAuthorize("@ss.has('eng:sample:approve')")
    public CommonResult<Void> createProdOrder(@PathVariable Long id) {
        service.createProdOrder(id);
        return CommonResult.success();
    }

    @Operation(summary = "申请出库：生成仓库其他出库草稿，返回出库单 ID")
    @PostMapping("/{id}/request-stock-out")
    @PreAuthorize("@ss.has('eng:sample:ship')")
    public CommonResult<String> requestStockOut(@PathVariable Long id) {
        return CommonResult.success(service.requestStockOut(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("@ss.has('eng:sample:ship')")
    public CommonResult<Void> ship(@PathVariable Long id, @Valid @RequestBody ShipReq req) {
        service.ship(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("@ss.has('eng:sample:feedback')")
    public CommonResult<Void> feedback(@PathVariable Long id, @Valid @RequestBody FeedbackReq req) {
        service.feedback(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.has('eng:sample:close')")
    public CommonResult<Void> close(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonReq req) {
        service.close(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('eng:sample:void')")
    public CommonResult<Void> voidSample(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        service.voidSample(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('eng:sample:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
