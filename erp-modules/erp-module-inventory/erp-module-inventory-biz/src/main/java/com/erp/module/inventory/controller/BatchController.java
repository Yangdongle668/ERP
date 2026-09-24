package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.inventory.controller.vo.BatchVOs.AvailableBatch;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchDetail;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchQuery;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchRow;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchUpdate;
import com.erp.module.inventory.controller.vo.BatchVOs.ReasonReq;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialHistory;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialQuery;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialRow;
import com.erp.module.inventory.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;

/** 批次与序列号（需求 08-07 第 5 节） */
@Tag(name = "仓库 - 批次与序列号")
@RestController("invBatchController")
@RequestMapping("/api/inventory")
public class BatchController {

    private final BatchService service;

    public BatchController(BatchService service) {
        this.service = service;
    }

    @GetMapping("/batches")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<PageResult<BatchRow>> page(@Valid BatchQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "BatchSelect（登录即可）：本仓库某物料有库存的批次，按 FIFO 排序")
    @GetMapping("/batches/available")
    public CommonResult<List<AvailableBatch>> available(@RequestParam Long materialId, @RequestParam Long warehouseId) {
        return CommonResult.success(service.available(materialId, warehouseId));
    }

    @Operation(summary = "批次详情：档案、库存分布、流水")
    @GetMapping("/batches/{id}")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<BatchDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/batches/{id}/freeze")
    @PreAuthorize("@ss.has('inv:batch:freeze')")
    public CommonResult<Void> freeze(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        service.freezeById(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/batches/{id}/unfreeze")
    @PreAuthorize("@ss.has('inv:batch:freeze')")
    public CommonResult<Void> unfreeze(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        service.unfreezeById(id, req.reason());
        return CommonResult.success();
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize("@ss.has('inv:batch:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody BatchUpdate req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @GetMapping("/serials")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<PageResult<SerialRow>> serials(@Valid SerialQuery q) {
        return CommonResult.success(service.serials(q));
    }

    @GetMapping("/serials/{id}/history")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<List<SerialHistory>> serialHistory(@PathVariable Long id) {
        return CommonResult.success(service.serialHistory(id));
    }
}
