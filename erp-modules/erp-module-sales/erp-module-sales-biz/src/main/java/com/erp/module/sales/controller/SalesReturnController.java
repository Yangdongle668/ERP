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
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.ReturnVOs.JudgeReq;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnDetail;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnQuery;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnRow;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnSave;
import com.erp.module.sales.controller.vo.ReturnVOs.ShippedLine;
import com.erp.module.sales.service.returns.ReturnService;

import java.util.List;
import java.util.Map;

/** 销售退货（需求 04-06） */
@Tag(name = "销售 - 退货")
@RestController
@RequestMapping("/api/sales")
public class SalesReturnController {

    private final ReturnService service;

    public SalesReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping("/returns")
    @PreAuthorize("@ss.has('sales:return:query')")
    public CommonResult<PageResult<ReturnRow>> page(@Valid ReturnQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/returns/{id}")
    @PreAuthorize("@ss.has('sales:return:query')")
    public CommonResult<ReturnDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    /** 退货选单：该客户已出货的订单行 */
    @GetMapping("/shipped-lines")
    @PreAuthorize("@ss.hasAny('sales:return:create', 'sales:return:update')")
    public CommonResult<List<ShippedLine>> shippedLines(@RequestParam Long customerId, @RequestParam(required = false) Long materialId) {
        return CommonResult.success(service.shippedLines(customerId, materialId));
    }

    @PostMapping("/returns")
    @PreAuthorize("@ss.has('sales:return:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody ReturnSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/returns/{id}")
    @PreAuthorize("@ss.has('sales:return:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody ReturnSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/returns/{id}")
    @PreAuthorize("@ss.has('sales:return:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/returns/{id}/submit")
    @PreAuthorize("@ss.has('sales:return:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/returns/{id}/void")
    @PreAuthorize("@ss.has('sales:return:void')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    /** 登记判定结果（品质模块未上线时由业务录入） */
    @PostMapping("/returns/{id}/judge")
    @PreAuthorize("@ss.has('sales:return:update')")
    public CommonResult<Void> judge(@PathVariable Long id, @Valid @RequestBody List<JudgeReq> req) {
        service.judge(id, req);
        return CommonResult.success();
    }

    @GetMapping("/returns/{id}/print-data")
    @PreAuthorize("@ss.has('sales:return:print')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(service.printData(id));
    }
}
