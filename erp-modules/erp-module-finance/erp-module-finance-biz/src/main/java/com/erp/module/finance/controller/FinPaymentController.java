package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.finance.controller.vo.PayVOs.PaymentDetail;
import com.erp.module.finance.controller.vo.PayVOs.PaymentQuery;
import com.erp.module.finance.controller.vo.PayVOs.PaymentRow;
import com.erp.module.finance.controller.vo.PayVOs.PaymentSave;
import com.erp.module.finance.controller.vo.PayVOs.PurchaseOrderOption;
import com.erp.module.finance.controller.vo.PayVOs.RequestDetail;
import com.erp.module.finance.controller.vo.PayVOs.RequestQuery;
import com.erp.module.finance.controller.vo.PayVOs.RequestResult;
import com.erp.module.finance.controller.vo.PayVOs.RequestRow;
import com.erp.module.finance.controller.vo.PayVOs.RequestSave;
import com.erp.module.finance.controller.vo.PayVOs.SupplierBankOption;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.payment.PaymentRequestService;
import com.erp.module.finance.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "财务 - 付款申请 / 付款单")
@RestController
@RequestMapping("/api/finance")
public class FinPaymentController {

    static final List<ExcelColumn<PaymentRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", PaymentRow::docNo),
            ExcelColumn.text("requestNo", "付款申请", PaymentRow::requestNo),
            ExcelColumn.text("supplierName", "供应商", PaymentRow::supplierName),
            ExcelColumn.text("bankAccountName", "付款账户", PaymentRow::bankAccountName),
            ExcelColumn.date("payDate", "付款日期", PaymentRow::payDate),
            ExcelColumn.text("currency", "币别", PaymentRow::currency),
            ExcelColumn.number("amount", "金额", PaymentRow::amount),
            ExcelColumn.number("bankFee", "手续费", PaymentRow::bankFee),
            ExcelColumn.number("amountBase", "本位币", PaymentRow::amountBase),
            ExcelColumn.text("status", "状态", r -> CashStatus.valueOf(r.status()).label()),
            ExcelColumn.text("ownerName", "出纳", PaymentRow::ownerName));

    private final PaymentRequestService requestService;
    private final PaymentService service;
    private final FinExportSupport exportSupport;

    public FinPaymentController(PaymentRequestService requestService, PaymentService service, FinExportSupport exportSupport) {
        this.requestService = requestService;
        this.service = service;
        this.exportSupport = exportSupport;
    }

    // ==================== 付款申请 ====================

    @GetMapping("/payment-requests")
    @PreAuthorize("@ss.has('fin:payment-request:query')")
    public CommonResult<PageResult<RequestRow>> requests(@Valid RequestQuery q) {
        return CommonResult.success(requestService.page(q));
    }

    @GetMapping("/payment-requests/{id}")
    @PreAuthorize("@ss.hasAny('fin:payment-request:query','fin:payment:query')")
    public CommonResult<RequestDetail> request(@PathVariable Long id) {
        return CommonResult.success(requestService.detail(id));
    }

    @GetMapping("/payment-requests/supplier-banks")
    @PreAuthorize("@ss.has('fin:payment-request:query')")
    public CommonResult<List<SupplierBankOption>> supplierBanks(@RequestParam Long supplierId) {
        return CommonResult.success(requestService.supplierBanks(supplierId));
    }

    @GetMapping("/payment-requests/order-options")
    @PreAuthorize("@ss.has('fin:payment-request:query')")
    public CommonResult<List<PurchaseOrderOption>> orderOptions(@RequestParam Long supplierId) {
        return CommonResult.success(requestService.orderOptions(supplierId));
    }

    @PostMapping("/payment-requests")
    @PreAuthorize("@ss.has('fin:payment-request:create')")
    public CommonResult<RequestResult> createRequest(@Valid @RequestBody RequestSave req) {
        return CommonResult.success(requestService.create(req));
    }

    @PutMapping("/payment-requests/{id}")
    @PreAuthorize("@ss.has('fin:payment-request:create')")
    public CommonResult<RequestResult> updateRequest(@PathVariable Long id, @Valid @RequestBody RequestSave req) {
        return CommonResult.success(requestService.update(id, req));
    }

    @PostMapping("/payment-requests/{id}/submit")
    @PreAuthorize("@ss.has('fin:payment-request:submit')")
    public CommonResult<RequestResult> submit(@PathVariable Long id) {
        return CommonResult.success(requestService.submit(id));
    }

    @PostMapping("/payment-requests/{id}/withdraw")
    @PreAuthorize("@ss.has('fin:payment-request:submit')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        requestService.withdraw(id);
        return CommonResult.success();
    }

    @PostMapping("/payment-requests/{id}/close")
    @PreAuthorize("@ss.has('fin:payment-request:submit')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        requestService.close(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/payment-requests/{id}/void")
    @PreAuthorize("@ss.has('fin:payment-request:create')")
    public CommonResult<Void> voidRequest(@PathVariable Long id, @RequestBody ReasonReq req) {
        requestService.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/payment-requests/{id}/print-data")
    @PreAuthorize("@ss.hasAny('fin:payment-request:query','fin:payment:query')")
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id) {
        return CommonResult.success(requestService.printData(id));
    }

    // ==================== 付款单 ====================

    @GetMapping("/payments")
    @PreAuthorize("@ss.has('fin:payment:query')")
    public CommonResult<PageResult<PaymentRow>> page(@Valid PaymentQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/payments/export")
    @PreAuthorize("@ss.has('fin:payment:query')")
    public void export(@Valid PaymentQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "付款单", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("@ss.has('fin:payment:query')")
    public CommonResult<PaymentDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/payments")
    @PreAuthorize("@ss.has('fin:payment:create')")
    public CommonResult<Long> create(@Valid @RequestBody PaymentSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/payments/{id}")
    @PreAuthorize("@ss.has('fin:payment:create')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody PaymentSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize("@ss.has('fin:payment:create')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("@ss.has('fin:payment:create')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/payments/{id}/confirm")
    @PreAuthorize("@ss.has('fin:payment:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return CommonResult.success();
    }

    @PostMapping("/payments/{id}/unconfirm")
    @PreAuthorize("@ss.has('fin:payment:confirm')")
    public CommonResult<Void> unconfirm(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.unconfirm(id, req.reason());
        return CommonResult.success();
    }
}
