package com.erp.module.purchase.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustDetail;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustQuery;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustRow;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustSave;
import com.erp.module.purchase.controller.vo.PriceVOs.EffectivePrice;
import com.erp.module.purchase.controller.vo.PriceVOs.PriceQuery;
import com.erp.module.purchase.controller.vo.PriceVOs.PriceRow;
import com.erp.module.purchase.service.PurExportSupport;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.price.PriceService;
import io.swagger.v3.oas.annotations.Operation;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 采购价格与调价单（需求 07-02 第 5 节）。价格列表、历史价格需要字段权限 pur:price:view（R06） */
@Tag(name = "资材 - 采购价格")
@RestController
@RequestMapping("/api/purchase")
public class PriceController {

    static final Map<String, String> PRICE_STATUS = Map.of("EFFECTIVE", "有效", "EXPIRED", "过期", "REPLACED", "被替代");

    static final List<ExcelColumn<PriceRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("supplierName", "供应商", PriceRow::supplierName),
            ExcelColumn.text("materialCode", "物料编码", PriceRow::materialCode),
            ExcelColumn.text("materialName", "名称", PriceRow::materialName),
            ExcelColumn.text("materialSpec", "规格", PriceRow::materialSpec),
            ExcelColumn.text("baseUom", "单位", PriceRow::baseUom),
            ExcelColumn.text("currency", "币别", PriceRow::currency),
            ExcelColumn.number("minQty", "阶梯起始数量", PriceRow::minQty),
            ExcelColumn.number("price", "不含税单价", PriceRow::price),
            ExcelColumn.number("taxRate", "税率", PriceRow::taxRate),
            ExcelColumn.number("priceInclTax", "含税单价", PriceRow::priceInclTax),
            ExcelColumn.date("effectiveFrom", "生效日期", PriceRow::effectiveFrom),
            ExcelColumn.date("effectiveTo", "失效日期", PriceRow::effectiveTo),
            ExcelColumn.text("priceStatus", "状态", r -> PRICE_STATUS.get(r.priceStatus())),
            ExcelColumn.text("adjustNo", "来源调价单", PriceRow::adjustNo));

    private final PriceService service;
    private final PurExportSupport exportSupport;

    public PriceController(PriceService service, PurExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/prices")
    @PreAuthorize("@ss.has('pur:price:query') and @ss.has('pur:price:view')")
    public CommonResult<PageResult<PriceRow>> page(@Valid PriceQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/prices/history")
    @PreAuthorize("@ss.has('pur:price:query') and @ss.has('pur:price:view')")
    public CommonResult<List<PriceRow>> history(@RequestParam Long supplierId, @RequestParam Long materialId) {
        return CommonResult.success(service.history(supplierId, materialId));
    }

    @Operation(summary = "取价（采购订单、申请、委外使用）：按供应商、物料、数量（业务单位）、日期、币别；没有有效价格返回空")
    @GetMapping("/prices/effective")
    @PreAuthorize("@ss.has('pur:price:view')")
    public CommonResult<EffectivePrice> effective(@RequestParam Long supplierId, @RequestParam Long materialId,
                                                  @RequestParam(required = false) BigDecimal qty, @RequestParam(required = false) String uom,
                                                  @RequestParam(required = false) LocalDate date, @RequestParam String currency) {
        return CommonResult.success(service.effectiveForUom(supplierId, materialId, qty, uom, date, currency));
    }

    @GetMapping("/prices/export")
    @PreAuthorize("@ss.has('pur:price:export') and @ss.has('pur:price:view')")
    public void export(@Valid PriceQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "采购价格", EXPORT_COLUMNS, q.getColumns(), limit -> service.listForExport(q, limit));
    }

    // ==================== 调价单 ====================

    @GetMapping("/price-adjusts")
    @PreAuthorize("@ss.has('pur:price:query')")
    public CommonResult<PageResult<AdjustRow>> adjustPage(@Valid AdjustQuery q) {
        return CommonResult.success(service.adjustPage(q));
    }

    @GetMapping("/price-adjusts/{id}")
    @PreAuthorize("@ss.has('pur:price:query') and @ss.has('pur:price:view')")
    public CommonResult<AdjustDetail> adjustDetail(@PathVariable Long id) {
        return CommonResult.success(service.adjustDetail(id));
    }

    @PostMapping("/price-adjusts")
    @PreAuthorize("@ss.has('pur:price:adjust') and @ss.has('pur:price:view')")
    public CommonResult<Long> create(@Valid @RequestBody AdjustSave req) {
        return CommonResult.success(service.createAdjust(req));
    }

    @PutMapping("/price-adjusts/{id}")
    @PreAuthorize("@ss.has('pur:price:adjust') and @ss.has('pur:price:view')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody AdjustSave req) {
        service.updateAdjust(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/price-adjusts/{id}")
    @PreAuthorize("@ss.has('pur:price:adjust')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.deleteAdjust(id);
        return CommonResult.success();
    }

    @PostMapping("/price-adjusts/{id}/submit")
    @PreAuthorize("@ss.has('pur:price:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/price-adjusts/{id}/void")
    @PreAuthorize("@ss.has('pur:price:adjust')")
    public CommonResult<Void> voidAdjust(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        service.voidAdjust(id, req == null ? null : req.reason());
        return CommonResult.success();
    }

    @GetMapping("/price-adjusts/import-template")
    @PreAuthorize("@ss.has('pur:price:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "采购价格", PriceService.IMPORT_COLUMNS);
    }

    @PostMapping(value = "/price-adjusts/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('pur:price:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, PriceService.IMPORT_COLUMNS);
        Map<Integer, String> actions = service.checkImport(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "采购价格导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(PriceService.IMPORT_COLUMNS, rows, r -> actions.get(r.rowNo())));
    }

    @Operation(summary = "导入价格：按供应商 + 币别生成草稿调价单")
    @PostMapping(value = "/price-adjusts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('pur:price:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean partial) {
        if (!PurSupport.canViewPrice()) throw new BizException(GlobalErrorCodes.FORBIDDEN);
        List<ImportRow> rows = ExcelSupport.read(file, PriceService.IMPORT_COLUMNS);
        service.checkImport(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(service.doImport(rows.stream().filter(r -> !r.hasError()).toList()));
    }
}
