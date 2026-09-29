package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.ApVOs.ApDetail;
import com.erp.module.finance.controller.vo.ApVOs.ApQuery;
import com.erp.module.finance.controller.vo.ApVOs.ApRow;
import com.erp.module.finance.controller.vo.ApVOs.CertifyReq;
import com.erp.module.finance.controller.vo.ApVOs.OtherApSave;
import com.erp.module.finance.controller.vo.ApVOs.PayableCandidate;
import com.erp.module.finance.controller.vo.ApVOs.PiDetail;
import com.erp.module.finance.controller.vo.ApVOs.PiQuery;
import com.erp.module.finance.controller.vo.ApVOs.PiRow;
import com.erp.module.finance.controller.vo.ApVOs.PiSave;
import com.erp.module.finance.controller.vo.ApVOs.UninvoicedApLine;
import com.erp.module.finance.controller.vo.ArVOs.SubmitResult;
import com.erp.module.finance.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.ap.PayableService;
import com.erp.module.finance.service.ap.PurchaseInvoiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "财务 - 应付 / 进项发票")
@RestController
@RequestMapping("/api/finance")
public class FinPayableController {

    static final Map<String, String> AP_TYPES = Map.of("PURCHASE", "采购", "OUTSOURCE", "委外加工费", "OTHER", "其他");

    static final List<ExcelColumn<ApRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", ApRow::docNo),
            ExcelColumn.text("apType", "类型", r -> AP_TYPES.getOrDefault(r.apType(), r.apType())),
            ExcelColumn.text("supplierName", "供应商", ApRow::supplierName),
            ExcelColumn.text("statementNo", "对账单", ApRow::statementNo),
            ExcelColumn.date("bizDate", "业务日期", ApRow::bizDate),
            ExcelColumn.text("currency", "币别", ApRow::currency),
            ExcelColumn.number("totalAmount", "价税合计", ApRow::totalAmount),
            ExcelColumn.number("totalAmountBase", "本位币", ApRow::totalAmountBase),
            ExcelColumn.number("invoicedAmount", "已匹配发票", ApRow::invoicedAmount),
            ExcelColumn.number("requestedAmount", "已申请付款", ApRow::requestedAmount),
            ExcelColumn.number("verifiedAmount", "已付款", ApRow::verifiedAmount),
            ExcelColumn.number("unpaidAmount", "未付款", ApRow::unpaidAmount),
            ExcelColumn.date("dueDate", "到期日", ApRow::dueDate),
            ExcelColumn.text("status", "状态", r -> ArStatus.valueOf(r.status()).label()));

    private final PayableService service;
    private final PurchaseInvoiceService invoiceService;
    private final FinExportSupport exportSupport;

    public FinPayableController(PayableService service, PurchaseInvoiceService invoiceService, FinExportSupport exportSupport) {
        this.service = service;
        this.invoiceService = invoiceService;
        this.exportSupport = exportSupport;
    }

    // ==================== 应付单 ====================

    @GetMapping("/payables")
    @PreAuthorize("@ss.has('fin:payable:query')")
    public CommonResult<PageResult<ApRow>> page(@Valid ApQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/payables/summary")
    @PreAuthorize("@ss.has('fin:payable:query')")
    public CommonResult<Map<String, BigDecimal>> summary(@Valid ApQuery q) {
        return CommonResult.success(service.summary(q));
    }

    @GetMapping("/payables/export")
    @PreAuthorize("@ss.has('fin:payable:export')")
    public void export(@Valid ApQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "应付单", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    /** 付款申请选择应付：已确认、可申请金额 > 0 */
    @GetMapping("/payables/payable-candidates")
    @PreAuthorize("@ss.hasAny('fin:payment-request:create','fin:payable:query')")
    public CommonResult<List<PayableCandidate>> candidates(@RequestParam Long supplierId, @RequestParam(required = false) String currency) {
        return CommonResult.success(service.candidates(supplierId, currency));
    }

    @GetMapping("/payables/{id}")
    @PreAuthorize("@ss.has('fin:payable:query')")
    public CommonResult<ApDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/payables/{id}/confirm")
    @PreAuthorize("@ss.has('fin:payable:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return CommonResult.success();
    }

    @PostMapping("/payables/{id}/unconfirm")
    @PreAuthorize("@ss.has('fin:payable:unconfirm')")
    public CommonResult<Void> unconfirm(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.unconfirm(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/payables/{id}/void")
    @PreAuthorize("@ss.has('fin:payable:unconfirm')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/payables/other")
    @PreAuthorize("@ss.has('fin:payable:create-other')")
    public CommonResult<Long> createOther(@Valid @RequestBody OtherApSave req) {
        return CommonResult.success(service.createOther(req));
    }

    @PutMapping("/payables/other/{id}")
    @PreAuthorize("@ss.has('fin:payable:create-other')")
    public CommonResult<Void> updateOther(@PathVariable Long id, @Valid @RequestBody OtherApSave req) {
        service.updateOther(id, req);
        return CommonResult.success();
    }

    @PostMapping("/payables/{id}/submit")
    @PreAuthorize("@ss.has('fin:payable:create-other')")
    public CommonResult<SubmitResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/payables/{id}/withdraw")
    @PreAuthorize("@ss.has('fin:payable:create-other')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return CommonResult.success();
    }

    // ==================== 进项发票 ====================

    @GetMapping("/purchase-invoices")
    @PreAuthorize("@ss.has('fin:payable:query')")
    public CommonResult<PageResult<PiRow>> invoices(@Valid PiQuery q) {
        return CommonResult.success(invoiceService.page(q));
    }

    @GetMapping("/purchase-invoices/{id}")
    @PreAuthorize("@ss.has('fin:payable:query')")
    public CommonResult<PiDetail> invoice(@PathVariable Long id) {
        return CommonResult.success(invoiceService.detail(id));
    }

    /** 可开票应付行（供应商已确认、未完全开票） */
    @GetMapping("/payable-lines/uninvoiced")
    @PreAuthorize("@ss.has('fin:payable:invoice')")
    public CommonResult<List<UninvoicedApLine>> uninvoiced(@RequestParam Long supplierId, @RequestParam(required = false) String currency,
                                                           @RequestParam(required = false) List<Long> payableIds) {
        return CommonResult.success(invoiceService.uninvoiced(supplierId, currency, payableIds));
    }

    @PostMapping("/purchase-invoices")
    @PreAuthorize("@ss.has('fin:payable:invoice')")
    public CommonResult<Long> register(@Valid @RequestBody PiSave req) {
        return CommonResult.success(invoiceService.register(req));
    }

    /** 差异确认（财务主管）：视为已匹配并生成价差调整应付行 */
    @PostMapping("/purchase-invoices/{id}/confirm-diff")
    @PreAuthorize("@ss.has('fin:payable:confirm')")
    public CommonResult<Void> confirmDiff(@PathVariable Long id) {
        invoiceService.confirmDiff(id);
        return CommonResult.success();
    }

    @PostMapping("/purchase-invoices/{id}/certify")
    @PreAuthorize("@ss.has('fin:payable:invoice')")
    public CommonResult<Void> certify(@PathVariable Long id, @Valid @RequestBody CertifyReq req) {
        invoiceService.certify(id, req.period());
        return CommonResult.success();
    }

    @PostMapping("/purchase-invoices/{id}/void")
    @PreAuthorize("@ss.has('fin:payable:invoice')")
    public CommonResult<Void> voidInvoice(@PathVariable Long id, @RequestBody ReasonReq req) {
        invoiceService.voidInvoice(id, req.reason());
        return CommonResult.success();
    }
}
