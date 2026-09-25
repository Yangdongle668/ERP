package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.CommonVOs.SaveResult;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptDetail;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptQuery;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptRow;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptSave;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReturnableLine;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReturnableQuery;
import com.erp.module.purchase.service.receipt.ReceiptService;
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

/** 到货（需求 07-06 第 6 节） */
@Tag(name = "资材 - 到货")
@RestController
@RequestMapping("/api/purchase")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping("/receipts")
    @PreAuthorize("@ss.has('pur:receipt:query')")
    public CommonResult<PageResult<ReceiptRow>> page(@Valid ReceiptQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('pur:receipt:query')")
    public CommonResult<ReceiptDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/receipts")
    @PreAuthorize("@ss.has('pur:receipt:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody ReceiptSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('pur:receipt:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody ReceiptSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('pur:receipt:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @Operation(summary = "审核：回写订单已到货，按入库仓生成仓库入库单")
    @PostMapping("/receipts/{id}/approve")
    @PreAuthorize("@ss.has('pur:receipt:approve')")
    public CommonResult<DocResult> approve(@PathVariable Long id) {
        return CommonResult.success(service.approve(id));
    }

    @PostMapping("/receipts/{id}/unapprove")
    @PreAuthorize("@ss.has('pur:receipt:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unapprove(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/receipts/{id}/print-data")
    @PreAuthorize("@ss.has('pur:receipt:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }

    @Operation(summary = "退货选单：已入库、有可退数量的到货行")
    @GetMapping("/receipt-lines/returnable")
    @PreAuthorize("@ss.hasAny('pur:return:create', 'pur:return:update')")
    public CommonResult<PageResult<ReturnableLine>> returnable(@Valid ReturnableQuery q) {
        return CommonResult.success(service.returnableLines(q));
    }
}
