package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.StatementVOs.BatchGenerateReq;
import com.erp.module.purchase.controller.vo.StatementVOs.ConfirmReq;
import com.erp.module.purchase.controller.vo.StatementVOs.LineResp;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementDetail;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementQuery;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementRow;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementSave;
import com.erp.module.purchase.service.statement.StatementService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 供应商对账（需求 07-09 第 6 节） */
@Tag(name = "资材 - 供应商对账")
@RestController
@RequestMapping("/api/purchase/statements")
public class StatementController {

    private final StatementService service;

    public StatementController(StatementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pur:statement:query')")
    public CommonResult<PageResult<StatementRow>> page(@Valid StatementQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "加载可对账明细（按剩余可对账数量）")
    @GetMapping("/candidates")
    @PreAuthorize("@ss.hasAny('pur:statement:create', 'pur:statement:update')")
    public CommonResult<List<LineResp>> candidates(@RequestParam Long supplierId, @RequestParam(required = false) String currency,
                                                   @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return CommonResult.success(service.candidates(supplierId, currency, from, to));
    }

    @PostMapping("/batch-generate")
    @PreAuthorize("@ss.has('pur:statement:create')")
    public CommonResult<List<Long>> batchGenerate(@Valid @RequestBody BatchGenerateReq req) {
        return CommonResult.success(service.batchGenerate(req.periodFrom(), req.periodTo()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pur:statement:query')")
    public CommonResult<StatementDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pur:statement:create')")
    public CommonResult<Long> create(@Valid @RequestBody StatementSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pur:statement:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody StatementSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pur:statement:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('pur:statement:delete')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('pur:statement:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/{id}/unapprove")
    @PreAuthorize("@ss.has('pur:statement:update')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unapprove(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @Operation(summary = "供应商已确认（R04）：必须上传签字对账单")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.has('pur:statement:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmReq req) {
        service.confirm(id, req.confirmer(), req.confirmedAt(), req.fileIds());
        return CommonResult.success();
    }

    @PostMapping("/{id}/unconfirm")
    @PreAuthorize("@ss.has('pur:statement:unconfirm')")
    public CommonResult<Void> unconfirm(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unconfirm(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('pur:statement:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
