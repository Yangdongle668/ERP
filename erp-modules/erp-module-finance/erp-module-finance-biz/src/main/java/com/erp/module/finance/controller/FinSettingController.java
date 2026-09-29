package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.finance.controller.vo.SettingVOs.AccountNode;
import com.erp.module.finance.controller.vo.SettingVOs.AccountOption;
import com.erp.module.finance.controller.vo.SettingVOs.AccountSave;
import com.erp.module.finance.controller.vo.SettingVOs.BankAccountSave;
import com.erp.module.finance.controller.vo.SettingVOs.BankAccountVO;
import com.erp.module.finance.controller.vo.SettingVOs.BankOption;
import com.erp.module.finance.controller.vo.SettingVOs.InitYearReq;
import com.erp.module.finance.controller.vo.SettingVOs.MappingSave;
import com.erp.module.finance.controller.vo.SettingVOs.MappingVO;
import com.erp.module.finance.controller.vo.SettingVOs.PeriodVO;
import com.erp.module.finance.service.payment.PaymentService;
import com.erp.module.finance.service.receipt.ReceiptService;
import com.erp.module.finance.service.setting.SettingService;
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

@Tag(name = "财务 - 基础设置")
@RestController
@RequestMapping("/api/finance")
public class FinSettingController {

    private final SettingService service;
    private final ReceiptService receiptService;
    private final PaymentService paymentService;

    public FinSettingController(SettingService service, ReceiptService receiptService, PaymentService paymentService) {
        this.service = service;
        this.receiptService = receiptService;
        this.paymentService = paymentService;
    }

    // ==================== 会计科目 ====================

    @GetMapping("/accounts")
    @PreAuthorize("@ss.has('fin:setting:query')")
    public CommonResult<List<AccountNode>> accountTree() {
        return CommonResult.success(service.accountTree());
    }

    /** 启用的末级科目（映射、凭证、银行账户选择） */
    @GetMapping("/accounts/options")
    @PreAuthorize("@ss.hasAny('fin:setting:query','fin:voucher:query')")
    public CommonResult<List<AccountOption>> accountOptions() {
        return CommonResult.success(service.accountOptions());
    }

    @PostMapping("/accounts")
    @PreAuthorize("@ss.has('fin:account:manage')")
    public CommonResult<Long> createAccount(@Valid @RequestBody AccountSave req) {
        return CommonResult.success(service.createAccount(req));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("@ss.has('fin:account:manage')")
    public CommonResult<Void> updateAccount(@PathVariable Long id, @Valid @RequestBody AccountSave req) {
        service.updateAccount(id, req);
        return CommonResult.success();
    }

    @PutMapping("/accounts/{id}/status")
    @PreAuthorize("@ss.has('fin:account:manage')")
    public CommonResult<Void> accountStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        service.setAccountStatus(id, enabled);
        return CommonResult.success();
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("@ss.has('fin:account:manage')")
    public CommonResult<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return CommonResult.success();
    }

    // ==================== 会计期间 ====================

    @GetMapping("/periods")
    @PreAuthorize("@ss.hasAny('fin:setting:query','fin:close:query')")
    public CommonResult<List<PeriodVO>> periods(@RequestParam(required = false) Integer year) {
        return CommonResult.success(service.periods(year));
    }

    @PostMapping("/periods/init-year")
    @PreAuthorize("@ss.has('fin:period:manage')")
    public CommonResult<Void> initYear(@Valid @RequestBody InitYearReq req) {
        service.initYear(req);
        return CommonResult.success();
    }

    // ==================== 银行账户 ====================

    @GetMapping("/bank-accounts")
    @PreAuthorize("@ss.has('fin:setting:query')")
    public CommonResult<List<BankAccountVO>> banks(@RequestParam(required = false) String keyword, @RequestParam(required = false) String currency,
                                                   @RequestParam(required = false) String status) {
        return CommonResult.success(service.bankAccounts(keyword, currency, status));
    }

    /** 收付款单中的账户下拉（启用） */
    @GetMapping("/bank-accounts/simple")
    @PreAuthorize("@ss.hasAny('fin:setting:query','fin:receipt:query','fin:payment:query','fin:report:query')")
    public CommonResult<List<BankOption>> bankOptions(@RequestParam(required = false) String currency) {
        return CommonResult.success(service.bankOptions(currency));
    }

    @PostMapping("/bank-accounts")
    @PreAuthorize("@ss.has('fin:bank:manage')")
    public CommonResult<Long> createBank(@Valid @RequestBody BankAccountSave req) {
        return CommonResult.success(service.createBank(req));
    }

    @PutMapping("/bank-accounts/{id}")
    @PreAuthorize("@ss.has('fin:bank:manage')")
    public CommonResult<Void> updateBank(@PathVariable Long id, @Valid @RequestBody BankAccountSave req) {
        service.updateBank(id, req);
        return CommonResult.success();
    }

    /** 已被收付款引用时改为停用 */
    @DeleteMapping("/bank-accounts/{id}")
    @PreAuthorize("@ss.has('fin:bank:manage')")
    public CommonResult<Void> deleteBank(@PathVariable Long id) {
        service.deleteBank(id, bankId -> receiptService.bankUsed(bankId) || paymentService.bankUsed(bankId));
        return CommonResult.success();
    }

    // ==================== 科目映射 ====================

    @GetMapping("/account-mappings")
    @PreAuthorize("@ss.has('fin:setting:query')")
    public CommonResult<List<MappingVO>> mappings(@RequestParam(required = false) String bizType) {
        return CommonResult.success(service.mappings(bizType));
    }

    @PostMapping("/account-mappings")
    @PreAuthorize("@ss.has('fin:mapping:manage')")
    public CommonResult<Long> createMapping(@Valid @RequestBody MappingSave req) {
        return CommonResult.success(service.createMapping(req));
    }

    @PutMapping("/account-mappings/{id}")
    @PreAuthorize("@ss.has('fin:mapping:manage')")
    public CommonResult<Void> updateMapping(@PathVariable Long id, @Valid @RequestBody MappingSave req) {
        service.updateMapping(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/account-mappings/{id}")
    @PreAuthorize("@ss.has('fin:mapping:manage')")
    public CommonResult<Void> deleteMapping(@PathVariable Long id) {
        service.deleteMapping(id);
        return CommonResult.success();
    }
}
