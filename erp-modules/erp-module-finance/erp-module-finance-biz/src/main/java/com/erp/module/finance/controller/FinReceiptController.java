package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.module.finance.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.finance.controller.vo.ReceiptVOs.BankImportResult;
import com.erp.module.finance.controller.vo.ReceiptVOs.OrderOption;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptDetail;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptQuery;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptRow;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptSave;
import com.erp.module.finance.controller.vo.VerifyVOs.Candidates;
import com.erp.module.finance.controller.vo.VerifyVOs.Pick;
import com.erp.module.finance.controller.vo.VerifyVOs.VerificationVO;
import com.erp.module.finance.controller.vo.VerifyVOs.VerifyReq;
import com.erp.module.finance.controller.vo.VerifyVOs.VerifyResult;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.receipt.ReceiptService;
import com.erp.module.finance.service.verify.VerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "财务 - 收款 / 核销")
@RestController
@RequestMapping("/api/finance")
public class FinReceiptController {

    static final Map<String, String> TYPES = Map.of("SALES", "销售收款", "ADVANCE", "预收款", "OTHER", "其他收款", "REFUND", "退款");

    static final List<ExcelColumn<ReceiptRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", ReceiptRow::docNo),
            ExcelColumn.text("customerName", "客户", ReceiptRow::customerName),
            ExcelColumn.text("receiptType", "类型", r -> TYPES.getOrDefault(r.receiptType(), r.receiptType())),
            ExcelColumn.text("bankAccountName", "收款账户", ReceiptRow::bankAccountName),
            ExcelColumn.date("receiptDate", "到账日期", ReceiptRow::receiptDate),
            ExcelColumn.text("currency", "币别", ReceiptRow::currency),
            ExcelColumn.number("amount", "到账金额", ReceiptRow::amount),
            ExcelColumn.number("bankFee", "手续费", ReceiptRow::bankFee),
            ExcelColumn.number("amountBase", "本位币", ReceiptRow::amountBase),
            ExcelColumn.number("allocatedAmount", "已核销", ReceiptRow::allocatedAmount),
            ExcelColumn.number("unallocatedAmount", "未核销", ReceiptRow::unallocatedAmount),
            ExcelColumn.text("orderNo", "订单号", ReceiptRow::orderNo),
            ExcelColumn.text("bankRefNo", "银行流水号", ReceiptRow::bankRefNo),
            ExcelColumn.text("status", "状态", r -> CashStatus.valueOf(r.status()).label()));

    private final ReceiptService service;
    private final VerificationService verificationService;
    private final FinExportSupport exportSupport;

    public FinReceiptController(ReceiptService service, VerificationService verificationService, FinExportSupport exportSupport) {
        this.service = service;
        this.verificationService = verificationService;
        this.exportSupport = exportSupport;
    }

    // ==================== 收款单 ====================

    @GetMapping("/receipts")
    @PreAuthorize("@ss.has('fin:receipt:query')")
    public CommonResult<PageResult<ReceiptRow>> page(@Valid ReceiptQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/receipts/export")
    @PreAuthorize("@ss.has('fin:receipt:query')")
    public void export(@Valid ReceiptQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "收款单", EXPORT_COLUMNS, columns, limit -> service.list(q).stream().limit(limit).toList());
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('fin:receipt:query')")
    public CommonResult<ReceiptDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    /** 预收款可选订单（客户未出完的订单） */
    @GetMapping("/receipts/order-options")
    @PreAuthorize("@ss.has('fin:receipt:query')")
    public CommonResult<List<OrderOption>> orderOptions(@RequestParam Long customerId) {
        return CommonResult.success(service.orderOptions(customerId));
    }

    @PostMapping("/receipts")
    @PreAuthorize("@ss.has('fin:receipt:create')")
    public CommonResult<Long> create(@Valid @RequestBody ReceiptSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('fin:receipt:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ReceiptSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/receipts/{id}")
    @PreAuthorize("@ss.has('fin:receipt:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/receipts/{id}/void")
    @PreAuthorize("@ss.has('fin:receipt:delete')")
    public CommonResult<Void> voidDoc(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.voidDoc(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/receipts/{id}/confirm")
    @PreAuthorize("@ss.has('fin:receipt:confirm')")
    public CommonResult<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return CommonResult.success();
    }

    @PostMapping("/receipts/{id}/unconfirm")
    @PreAuthorize("@ss.has('fin:receipt:unconfirm')")
    public CommonResult<Void> unconfirm(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.unconfirm(id, req.reason());
        return CommonResult.success();
    }

    @GetMapping("/receipts/import-template")
    @PreAuthorize("@ss.has('fin:receipt:create')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "银行流水", ReceiptService.IMPORT_COLUMNS);
    }

    /** 导入银行流水：按付款方名称匹配客户生成草稿收款单 */
    @PostMapping(value = "/receipts/import-bank", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('fin:receipt:create')")
    public CommonResult<BankImportResult> importBank(@RequestPart("file") MultipartFile file, @RequestParam Long bankAccountId,
                                                     @RequestParam(required = false) String settlementMethod) {
        return CommonResult.success(service.importBank(file, bankAccountId, settlementMethod));
    }

    // ==================== 核销 ====================

    @GetMapping("/verifications/candidates")
    @PreAuthorize("@ss.hasAny('fin:receipt:verify','fin:payment:verify')")
    public CommonResult<Candidates> candidates(@RequestParam(required = false) Long customerId, @RequestParam(required = false) Long supplierId,
                                               @RequestParam(required = false) String currency) {
        return CommonResult.success(customerId != null ? verificationService.receiptCandidates(customerId, currency)
                : verificationService.paymentCandidates(supplierId, currency));
    }

    @PostMapping("/verifications/receipt")
    @PreAuthorize("@ss.has('fin:receipt:verify')")
    public CommonResult<VerifyResult> verifyReceipt(@Valid @RequestBody VerifyReq req) {
        return CommonResult.success(verificationService.verifyReceipt(req));
    }

    /** 付款 / 预付冲应付 */
    @PostMapping({"/verifications/payment", "/verifications/prepay"})
    @PreAuthorize("@ss.has('fin:payment:verify')")
    public CommonResult<VerifyResult> verifyPayment(@Valid @RequestBody VerifyReq req) {
        return CommonResult.success(verificationService.verifyPayment(req));
    }

    /** 自动核销建议（不保存）：返回右侧各单据本次金额 */
    @PostMapping("/verifications/auto")
    @PreAuthorize("@ss.hasAny('fin:receipt:verify','fin:payment:verify')")
    public CommonResult<List<Pick>> auto(@RequestParam(defaultValue = "CUSTOMER") String partnerType, @Valid @RequestBody VerifyReq req) {
        return CommonResult.success(verificationService.auto(partnerType, req));
    }

    @PostMapping("/verifications/{id}/reverse")
    @PreAuthorize("@ss.hasAny('fin:receipt:unverify','fin:payment:verify')")
    public CommonResult<Void> reverse(@PathVariable Long id) {
        verificationService.reverse(id);
        return CommonResult.success();
    }

    /** 核销记录：按单据（docType + docId）或按往来单位 */
    @GetMapping("/verifications")
    @PreAuthorize("@ss.hasAny('fin:receipt:query','fin:payment:query','fin:receivable:query','fin:payable:query')")
    public CommonResult<List<VerificationVO>> list(@RequestParam(required = false) String docType, @RequestParam(required = false) Long docId,
                                                   @RequestParam(required = false) String partnerType, @RequestParam(required = false) Long partnerId,
                                                   @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                                   @RequestParam(required = false) Boolean includeReversed) {
        if (docType != null && docId != null) return CommonResult.success(verificationService.listByDoc(docType, docId));
        return CommonResult.success(verificationService.list(partnerType, partnerId, dateFrom, dateTo, includeReversed, 500));
    }
}
