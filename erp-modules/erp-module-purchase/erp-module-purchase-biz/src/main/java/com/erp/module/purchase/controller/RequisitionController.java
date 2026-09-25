package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.RequisitionVOs.PendingLine;
import com.erp.module.purchase.controller.vo.RequisitionVOs.PendingQuery;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqDetail;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqQuery;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqRow;
import com.erp.module.purchase.controller.vo.RequisitionVOs.ReqSave;
import com.erp.module.purchase.service.requisition.RequisitionService;
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

/** 采购申请（需求 07-03 第 5 节） */
@Tag(name = "资材 - 采购申请")
@RestController
@RequestMapping("/api/purchase")
public class RequisitionController {

    private final RequisitionService service;

    public RequisitionController(RequisitionService service) {
        this.service = service;
    }

    @GetMapping("/requisitions")
    @PreAuthorize("@ss.has('pur:requisition:query')")
    public CommonResult<PageResult<ReqRow>> page(@Valid ReqQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/requisitions/{id}")
    @PreAuthorize("@ss.has('pur:requisition:query')")
    public CommonResult<ReqDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/requisitions")
    @PreAuthorize("@ss.has('pur:requisition:create')")
    public CommonResult<Long> create(@Valid @RequestBody ReqSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/requisitions/{id}")
    @PreAuthorize("@ss.has('pur:requisition:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ReqSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/requisitions/{id}")
    @PreAuthorize("@ss.has('pur:requisition:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/requisitions/{id}/submit")
    @PreAuthorize("@ss.has('pur:requisition:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/requisitions/{id}/unapprove")
    @PreAuthorize("@ss.has('pur:requisition:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.unapprove(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @Operation(summary = "关闭（R06）：剩余未转行关闭，原因必填")
    @PostMapping("/requisitions/{id}/close")
    @PreAuthorize("@ss.has('pur:requisition:close')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.close(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @PostMapping("/requisitions/{id}/void")
    @PreAuthorize("@ss.has('pur:requisition:delete')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @Operation(summary = "待转订单明细（从申请生成订单的选单）")
    @GetMapping("/requisition-lines/pending")
    @PreAuthorize("@ss.hasAny('pur:requisition:query', 'pur:order:create')")
    public CommonResult<PageResult<PendingLine>> pending(@Valid PendingQuery q) {
        return CommonResult.success(service.pending(q));
    }
}
