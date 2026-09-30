package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.CommonVOs.BatchResult;
import com.erp.module.finance.controller.vo.CommonVOs.IdsReq;
import com.erp.module.finance.controller.vo.VoucherVOs.GenerateReq;
import com.erp.module.finance.controller.vo.VoucherVOs.GenerateResult;
import com.erp.module.finance.controller.vo.VoucherVOs.Pending;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherDetail;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherQuery;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherRow;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherSave;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.voucher.VoucherService;
import com.erp.module.finance.service.voucher.VoucherService.ExportRow;
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

@Tag(name = "财务 - 凭证")
@RestController
@RequestMapping("/api/finance/vouchers")
public class FinVoucherController {

    static final List<ExcelColumn<ExportRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("voucherNo", "凭证号", ExportRow::voucherNo),
            ExcelColumn.date("voucherDate", "日期", ExportRow::voucherDate),
            ExcelColumn.text("status", "状态", ExportRow::status),
            ExcelColumn.text("summary", "摘要", ExportRow::summary),
            ExcelColumn.text("accountCode", "科目编码", ExportRow::accountCode),
            ExcelColumn.text("accountName", "科目名称", ExportRow::accountName),
            ExcelColumn.number("debit", "借方金额", ExportRow::debit),
            ExcelColumn.number("credit", "贷方金额", ExportRow::credit),
            ExcelColumn.text("currency", "币别", ExportRow::currency),
            ExcelColumn.number("fcAmount", "原币金额", ExportRow::fcAmount),
            ExcelColumn.number("exchangeRate", "汇率", ExportRow::exchangeRate),
            ExcelColumn.text("aux", "辅助核算", ExportRow::aux));

    private final VoucherService service;
    private final FinExportSupport exportSupport;

    public FinVoucherController(VoucherService service, FinExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('fin:voucher:query')")
    public CommonResult<PageResult<VoucherRow>> page(@Valid VoucherQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('fin:voucher:export')")
    public void export(@Valid VoucherQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "凭证", EXPORT_COLUMNS, columns, limit -> service.exportRows(q, limit));
    }

    @GetMapping("/pending")
    @PreAuthorize("@ss.has('fin:voucher:query')")
    public CommonResult<List<Pending>> pending(@RequestParam String period) {
        return CommonResult.success(service.pending(period));
    }

    @PostMapping("/generate")
    @PreAuthorize("@ss.has('fin:voucher:create')")
    public CommonResult<GenerateResult> generate(@Valid @RequestBody GenerateReq req) {
        return CommonResult.success(service.generate(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('fin:voucher:query')")
    public CommonResult<VoucherDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('fin:voucher:create')")
    public CommonResult<Long> create(@Valid @RequestBody VoucherSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('fin:voucher:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody VoucherSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('fin:voucher:update')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("@ss.has('fin:voucher:audit')")
    public CommonResult<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/unaudit")
    @PreAuthorize("@ss.has('fin:voucher:audit')")
    public CommonResult<Void> unaudit(@PathVariable Long id) {
        service.unaudit(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("@ss.has('fin:voucher:post')")
    public CommonResult<Void> post(@PathVariable Long id) {
        service.post(id);
        return CommonResult.success();
    }

    @PostMapping("/{id}/unpost")
    @PreAuthorize("@ss.has('fin:voucher:unpost')")
    public CommonResult<Void> unpost(@PathVariable Long id) {
        service.unpost(id);
        return CommonResult.success();
    }

    @PostMapping("/batch-audit")
    @PreAuthorize("@ss.has('fin:voucher:audit')")
    public CommonResult<BatchResult> batchAudit(@RequestBody IdsReq req) {
        List<String> errors = service.batch(req.ids(), false);
        return CommonResult.success(new BatchResult(req.ids().size() - errors.size(), errors));
    }

    @PostMapping("/batch-post")
    @PreAuthorize("@ss.has('fin:voucher:post')")
    public CommonResult<BatchResult> batchPost(@RequestBody IdsReq req) {
        List<String> errors = service.batch(req.ids(), true);
        return CommonResult.success(new BatchResult(req.ids().size() - errors.size(), errors));
    }

    @PostMapping("/renumber")
    @PreAuthorize("@ss.has('fin:voucher:update')")
    public CommonResult<Map<String, Integer>> renumber(@RequestParam String period) {
        return CommonResult.success(Map.of("changed", service.renumber(period)));
    }
}
