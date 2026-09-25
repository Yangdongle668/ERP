package com.erp.module.crm.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.crm.controller.vo.CreditVOs.ChangeRow;
import com.erp.module.crm.controller.vo.CreditVOs.ChangeSave;
import com.erp.module.crm.controller.vo.CreditVOs.CreditQuery;
import com.erp.module.crm.controller.vo.CreditVOs.CreditRow;
import com.erp.module.crm.service.CreditService;
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

import java.util.List;

/** 客户信用（需求 03-03 第 5 节） */
@Tag(name = "CRM - 信用")
@RestController
@RequestMapping("/api/crm")
public class CreditController {

    private final CreditService service;

    public CreditController(CreditService service) {
        this.service = service;
    }

    @GetMapping("/credits")
    @PreAuthorize("@ss.has('crm:credit:query')")
    public CommonResult<PageResult<CreditRow>> page(@Valid CreditQuery q) {
        return CommonResult.success(service.page(q));
    }

    @PostMapping("/credits/refresh")
    @PreAuthorize("@ss.has('crm:credit:update')")
    public CommonResult<Void> refresh(@RequestBody List<Long> customerIds) {
        service.refresh(customerIds);
        return CommonResult.success();
    }

    @GetMapping("/credit-changes")
    @PreAuthorize("@ss.has('crm:credit:query')")
    public CommonResult<List<ChangeRow>> changes(@RequestParam Long customerId) {
        return CommonResult.success(service.changes(customerId));
    }

    /** 新建并提交调整单 */
    @PostMapping("/credit-changes")
    @PreAuthorize("@ss.has('crm:credit:update')")
    public CommonResult<ChangeRow> create(@Valid @RequestBody ChangeSave req) {
        return CommonResult.success(service.createAndSubmit(req));
    }

    @PostMapping("/credit-changes/{id}/submit")
    @PreAuthorize("@ss.has('crm:credit:update')")
    public CommonResult<Void> submit(@PathVariable Long id) {
        service.resubmit(id);
        return CommonResult.success();
    }

    @PostMapping("/credit-changes/{id}/void")
    @PreAuthorize("@ss.has('crm:credit:update')")
    public CommonResult<Void> voidChange(@PathVariable Long id) {
        service.voidChange(id);
        return CommonResult.success();
    }
}
