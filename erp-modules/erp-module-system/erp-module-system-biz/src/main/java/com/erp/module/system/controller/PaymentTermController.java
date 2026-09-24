package com.erp.module.system.controller;

import com.erp.common.enums.EnableStatus;
import com.erp.common.result.CommonResult;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.controller.vo.PaymentTermVOs.Country;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermResp;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermSave;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermSimple;
import com.erp.module.system.service.CountryService;
import com.erp.module.system.service.PaymentTermService;
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

import java.util.List;

/** 付款条件与贸易基础数据（01-14） */
@Tag(name = "系统管理 - 付款条件")
@RestController
@RequestMapping("/api/system")
public class PaymentTermController {

    private final PaymentTermService paymentTermService;
    private final CountryService countryService;

    public PaymentTermController(PaymentTermService paymentTermService, CountryService countryService) {
        this.paymentTermService = paymentTermService;
        this.countryService = countryService;
    }

    @GetMapping("/payment-terms")
    @PreAuthorize("@ss.has('system:payment-term:query')")
    public CommonResult<List<TermResp>> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String usage,
                                             @RequestParam(required = false) String status) {
        return CommonResult.success(paymentTermService.list(keyword, usage, status));
    }

    @GetMapping("/payment-terms/{id}")
    @PreAuthorize("@ss.has('system:payment-term:query')")
    public CommonResult<TermResp> get(@PathVariable Long id) {
        return CommonResult.success(paymentTermService.detail(id));
    }

    /** 启用的付款条件，登录即可；usage = SALES / PURCHASE */
    @GetMapping("/payment-terms/simple")
    public CommonResult<List<TermSimple>> simple(@RequestParam(required = false) String usage) {
        return CommonResult.success(paymentTermService.simple(usage));
    }

    @OperLog("新建付款条件")
    @PostMapping("/payment-terms")
    @PreAuthorize("@ss.has('system:payment-term:create')")
    public CommonResult<Long> create(@Valid @RequestBody TermSave req) {
        return CommonResult.success(paymentTermService.create(req));
    }

    @OperLog("修改付款条件")
    @PutMapping("/payment-terms/{id}")
    @PreAuthorize("@ss.has('system:payment-term:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody TermSave req) {
        paymentTermService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用付款条件")
    @PostMapping("/payment-terms/{id}/enable")
    @PreAuthorize("@ss.has('system:payment-term:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        paymentTermService.changeStatus(id, EnableStatus.ENABLED);
        return CommonResult.success();
    }

    @OperLog("停用付款条件")
    @PostMapping("/payment-terms/{id}/disable")
    @PreAuthorize("@ss.has('system:payment-term:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        paymentTermService.changeStatus(id, EnableStatus.DISABLED);
        return CommonResult.success();
    }

    @OperLog("删除付款条件")
    @DeleteMapping("/payment-terms/{id}")
    @PreAuthorize("@ss.has('system:payment-term:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        paymentTermService.delete(id);
        return CommonResult.success();
    }

    /** 国家/地区（CountrySelect），登录即可 */
    @GetMapping("/countries")
    public CommonResult<List<Country>> countries() {
        return CommonResult.success(countryService.list());
    }
}
