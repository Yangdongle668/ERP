package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.ArVOs.ArDetail;
import com.erp.module.finance.controller.vo.ArVOs.ArQuery;
import com.erp.module.finance.controller.vo.ArVOs.ArRow;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceDetail;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceQuery;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceRow;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceSave;
import com.erp.module.finance.controller.vo.ArVOs.OtherArSave;
import com.erp.module.finance.controller.vo.ArVOs.SubmitResult;
import com.erp.module.finance.controller.vo.ArVOs.UninvoicedLine;
import com.erp.module.finance.controller.vo.CommonVOs.BatchResult;
import com.erp.module.finance.controller.vo.CommonVOs.IdsReq;
import com.erp.module.finance.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.ar.ReceivableService;
import com.erp.module.finance.service.ar.SalesInvoiceService;
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

@Tag(name = "财务 - 应收 / 销项发票")
@RestController
@RequestMapping("/api/finance")
public class FinReceivableController {

    static final Map<String, String> AR_TYPES = Map.of("SALES", "销售出货", "SALES_RETURN", "退货", "DISCOUNT", "折让", "OTHER", "其他");

    static final List<ExcelColumn<ArRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", ArRow::docNo),
            ExcelColumn.text("arType", "类型", r -> AR_TYPES.getOrDefault(r.arType(), r.arType())),
            ExcelColumn.text("customerName", "客户", ArRow::customerName),
            ExcelColumn.text("sourceNo", "来源单号", ArRow::sourceNo),
            ExcelColumn.date("bizDate", "业务日期", ArRow::bizDate),
            ExcelColumn.text("currency", "币别", ArRow::currency),
            ExcelColumn.number("totalAmount", "价税合计", ArRow::totalAmount),
            ExcelColumn.number("totalAmountBase", "本位币", ArRow::totalAmountBase),
            ExcelColumn.number("verifiedAmount", "已核销", ArRow::verifiedAmount),
            ExcelColumn.number("unverifiedAmount", "未核销", ArRow::unverifiedAmount),
            ExcelColumn.number("invoicedAmount", "已开票", ArRow::invoicedAmount),
            ExcelColumn.date("dueDate", "到期日", ArRow::dueDate),
            ExcelColumn.number("overdueDays", "逾期天数", ArRow::overdueDays),
            ExcelColumn.text("status", "状态", r -> ArStatus.valueOf(r.status()).label()));

    private final ReceivableService service;
    private final SalesInvoiceService invoiceService;
    private final FinExportSupport exportSupport;

    public FinReceivableController(ReceivableService service, SalesInvoiceService invoiceService, FinExportSupport exportSupport) {
        this.service = service;
        this.invoiceService = invoiceService;
        this.exportSupport = exportSupport;
    }

    // ==================== 应收单 ====================

    @GetMapping("/receivables")
    @PreAuthorize("@ss.has('fin:receivable:query')")
    public CommonResult<PageResult<ArRow>> page(@Valid ArQuery q) {
        return CommonResult.success(service.page(q));
    }

    /** 列表底部合计（本位币） */
    @GetMapping("/receivables/summary")
    @PreAuthorize("@ss.has('fin:receivable:query')")
    public CommonResult<Map<String, BigDecimal>> summary(@Valid ArQuery q) {
        return CommonResult.success(service.summary(q));
    }

    @GetMapping("/receivables/export")
    @PreAuthorize("@ss.has('fin:receivable:export')")
    public void export(@Valid ArQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "应收单", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    @GetMapping("/receivables/{id}")
    @PreAuthorize("@ss.has('fin:receivable:query')")
    public CommonResult<ArDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/receivables/{id}/confirm")
    @PreAuthorize("@ss.has('fin:receivable:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return CommonResult.success();
    }

    @PostMapping("/receivables/batch-confirm")
    @PreAuthorize("@ss.has('fin:receivable:confirm')")
    public CommonResult<BatchResult> batchConfirm(@RequestBody IdsReq req) {
        List<String> errors = service.batchConfirm(req.ids());
        return CommonResult.success(new BatchResult((req.ids() == null ? 0 : req.ids().size()) - errors.size(), errors));
    }

    @PostMapping("/receivables/{id}/unconfirm")
    @PreAuthorize("@ss.has('fin:receivable:unconfirm')")
    public CommonResult<Void> unconfirm(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.unconfirm(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/receivables/{id}/void")
    @PreAuthorize("@ss.has('fin:receivable:unconfirm')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/receivables/other")
    @PreAuthorize("@ss.has('fin:receivable:create-other')")
    public CommonResult<Long> createOther(@Valid @RequestBody OtherArSave req) {
        return CommonResult.success(service.createOther(req));
    }

    @PutMapping("/receivables/other/{id}")
    @PreAuthorize("@ss.has('fin:receivable:create-other')")
    public CommonResult<Void> updateOther(@PathVariable Long id, @Valid @RequestBody OtherArSave req) {
        service.updateOther(id, req);
        return CommonResult.success();
    }

    @PostMapping("/receivables/{id}/submit")
    @PreAuthorize("@ss.has('fin:receivable:create-other')")
    public CommonResult<SubmitResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/receivables/{id}/withdraw")
    @PreAuthorize("@ss.has('fin:receivable:create-other')")
    public CommonResult<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return CommonResult.success();
    }

    // ==================== 销项发票登记 ====================

    @GetMapping("/sales-invoices")
    @PreAuthorize("@ss.has('fin:receivable:query')")
    public CommonResult<PageResult<InvoiceRow>> invoices(@Valid InvoiceQuery q) {
        return CommonResult.success(invoiceService.page(q));
    }

    @GetMapping("/sales-invoices/{id}")
    @PreAuthorize("@ss.has('fin:receivable:query')")
    public CommonResult<InvoiceDetail> invoice(@PathVariable Long id) {
        return CommonResult.success(invoiceService.detail(id));
    }

    /** 可开票的应收行（客户已确认、未完全开票） */
    @GetMapping("/sales-invoices/uninvoiced-lines")
    @PreAuthorize("@ss.has('fin:receivable:invoice')")
    public CommonResult<List<UninvoicedLine>> uninvoiced(@RequestParam Long customerId, @RequestParam(required = false) String currency,
                                                         @RequestParam(required = false) List<Long> receivableIds) {
        return CommonResult.success(invoiceService.uninvoiced(customerId, currency, receivableIds));
    }

    @PostMapping("/sales-invoices")
    @PreAuthorize("@ss.has('fin:receivable:invoice')")
    public CommonResult<Long> register(@Valid @RequestBody InvoiceSave req) {
        return CommonResult.success(invoiceService.register(req));
    }

    @PostMapping("/sales-invoices/{id}/void")
    @PreAuthorize("@ss.has('fin:receivable:invoice')")
    public CommonResult<Void> voidInvoice(@PathVariable Long id, @RequestBody ReasonReq req) {
        invoiceService.voidInvoice(id, req.reason(), false);
        return CommonResult.success();
    }

    @PostMapping("/sales-invoices/{id}/red")
    @PreAuthorize("@ss.has('fin:receivable:invoice')")
    public CommonResult<Void> red(@PathVariable Long id, @RequestBody ReasonReq req) {
        invoiceService.voidInvoice(id, req.reason(), true);
        return CommonResult.success();
    }
}
