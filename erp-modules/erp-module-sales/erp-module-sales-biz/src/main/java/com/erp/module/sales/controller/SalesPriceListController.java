package com.erp.module.sales.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.sales.api.price.SalesPriceDTO;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.sales.controller.vo.PriceListVOs.ItemExportRow;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListDetail;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListQuery;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListRow;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListSave;
import com.erp.module.sales.service.SalExportSupport;
import com.erp.module.sales.service.price.PriceListService;
import com.erp.module.sales.service.price.PriceLookupService;
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

/** 销售价格表与取价（需求 04-01） */
@Tag(name = "销售 - 价格表")
@RestController
@RequestMapping("/api/sales")
public class SalesPriceListController {

    static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("materialCode", "物料编码", true, null),
            ExcelColumn.input("uom", "单位", true, "销售单位"),
            ExcelColumn.input("minQty", "起始数量", true, "第一档填 0"),
            ExcelColumn.input("price", "单价", true, "按价格表的含税设置"),
            ExcelColumn.input("remark", "备注", false, null));

    static final List<ExcelColumn<ItemExportRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "编号", ItemExportRow::docNo),
            ExcelColumn.text("name", "名称", ItemExportRow::name),
            ExcelColumn.text("scope", "适用范围", ItemExportRow::scope),
            ExcelColumn.text("customerName", "客户", ItemExportRow::customerName),
            ExcelColumn.text("currency", "币别", ItemExportRow::currency),
            ExcelColumn.text("taxIncluded", "含税", r -> r.taxIncluded() ? "是" : "否"),
            ExcelColumn.date("effectiveFrom", "生效日期", ItemExportRow::effectiveFrom),
            ExcelColumn.date("effectiveTo", "失效日期", ItemExportRow::effectiveTo),
            ExcelColumn.text("status", "状态", ItemExportRow::status),
            ExcelColumn.text("materialCode", "物料编码", ItemExportRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", ItemExportRow::materialName),
            ExcelColumn.text("uom", "单位", ItemExportRow::uom),
            ExcelColumn.number("minQty", "起始数量", ItemExportRow::minQty),
            ExcelColumn.number("price", "单价", ItemExportRow::price));

    private final PriceListService service;
    private final PriceLookupService lookupService;
    private final SalExportSupport exportSupport;

    public SalesPriceListController(PriceListService service, PriceLookupService lookupService, SalExportSupport exportSupport) {
        this.service = service;
        this.lookupService = lookupService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/price-lists")
    @PreAuthorize("@ss.has('sales:price-list:query')")
    public CommonResult<PageResult<PriceListRow>> page(@Valid PriceListQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/price-lists/{id}")
    @PreAuthorize("@ss.has('sales:price-list:query')")
    public CommonResult<PriceListDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/price-lists")
    @PreAuthorize("@ss.has('sales:price-list:create')")
    public CommonResult<Long> create(@Valid @RequestBody PriceListSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/price-lists/{id}")
    @PreAuthorize("@ss.has('sales:price-list:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody PriceListSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/price-lists/{id}")
    @PreAuthorize("@ss.has('sales:price-list:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/price-lists/{id}/submit")
    @PreAuthorize("@ss.has('sales:price-list:submit')")
    public CommonResult<DocResult> submit(@PathVariable Long id) {
        return CommonResult.success(service.submit(id));
    }

    @PostMapping("/price-lists/{id}/close")
    @PreAuthorize("@ss.has('sales:price-list:update')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.close(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/price-lists/{id}/copy")
    @PreAuthorize("@ss.has('sales:price-list:create')")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.success(service.copy(id));
    }

    @GetMapping("/price-lists/export")
    @PreAuthorize("@ss.has('sales:price-list:export')")
    public void export(@Valid PriceListQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "销售价格表", EXPORT_COLUMNS, q.getColumns(), limit -> service.exportItems(q, limit));
    }

    @GetMapping("/price-lists/import-template")
    @PreAuthorize("@ss.has('sales:price-list:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "价格表明细", IMPORT_COLUMNS);
    }

    @PostMapping(value = "/price-lists/{id}/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('sales:price-list:import')")
    public CommonResult<ImportCheckResult> importCheck(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report, HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        service.checkImport(id, rows);
        if (report) {
            ExcelSupport.writeBytes(response, "价格表导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, r -> r.hasError() ? null : "UPSERT"));
    }

    @PostMapping(value = "/price-lists/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('sales:price-list:import')")
    public CommonResult<ImportResult> doImport(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                               @RequestParam(defaultValue = "false") boolean partial) {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        service.checkImport(id, rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(service.doImport(id, rows.stream().filter(r -> !r.hasError()).toList()));
    }

    /** 取价（报价单、订单录入时调用）；没有价格返回 null */
    @GetMapping("/prices/lookup")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<SalesPriceDTO> lookup(@RequestParam(required = false) Long customerId, @RequestParam Long materialId,
                                              @RequestParam(required = false) BigDecimal qty, @RequestParam String uom,
                                              @RequestParam(required = false) LocalDate date, @RequestParam String currency) {
        return CommonResult.success(lookupService.getPrice(customerId, materialId, qty, uom, date, currency).orElse(null));
    }
}
