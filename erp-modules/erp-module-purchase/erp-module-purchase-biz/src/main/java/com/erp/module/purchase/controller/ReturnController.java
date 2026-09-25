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
import com.erp.module.purchase.controller.vo.CommonVOs.SaveResult;
import com.erp.module.purchase.controller.vo.ReturnVOs.DefectCandidate;
import com.erp.module.purchase.controller.vo.ReturnVOs.FromDefectsReq;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnDetail;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnQuery;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnRow;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnSave;
import com.erp.module.purchase.service.receipt.ReturnService;

import java.util.List;
import java.util.Map;

/** 采购退货（需求 07-08 第 5 节） */
@Tag(name = "资材 - 采购退货")
@RestController
@RequestMapping("/api/purchase/returns")
public class ReturnController {

    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('pur:return:query')")
    public CommonResult<PageResult<ReturnRow>> page(@Valid ReturnQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "从不良品生成的候选")
    @GetMapping("/defect-candidates")
    @PreAuthorize("@ss.has('pur:return:create')")
    public CommonResult<List<DefectCandidate>> defectCandidates(@RequestParam(required = false) Long supplierId) {
        return CommonResult.success(service.defectCandidates(supplierId));
    }

    @PostMapping("/from-defects")
    @PreAuthorize("@ss.has('pur:return:create')")
    public CommonResult<List<Long>> fromDefects(@Valid @RequestBody FromDefectsReq req) {
        return CommonResult.success(service.fromDefects(req.items()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('pur:return:query')")
    public CommonResult<ReturnDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('pur:return:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody ReturnSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('pur:return:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody ReturnSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('pur:return:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.has('pur:return:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@ss.has('pur:return:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidDoc(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize("@ss.has('pur:return:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
