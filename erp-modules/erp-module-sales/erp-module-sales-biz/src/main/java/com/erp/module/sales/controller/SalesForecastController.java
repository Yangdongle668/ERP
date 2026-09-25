package com.erp.module.sales.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
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
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.sales.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.sales.controller.vo.ForecastVOs.ConsumptionRow;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastDetail;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastQuery;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastRow;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastSave;
import com.erp.module.sales.controller.vo.ForecastVOs.RowResp;
import com.erp.module.sales.service.forecast.ForecastService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 销售预测（需求 04-05） */
@Tag(name = "销售 - 预测")
@RestController
@RequestMapping("/api/sales")
public class SalesForecastController {

    static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("customerCode", "客户编码", false, "为空表示不区分客户"),
            ExcelColumn.input("materialCode", "物料编码", true, "成品 / 半成品"),
            ExcelColumn.input("period", "月份", true, "yyyyMM，如 202610"),
            ExcelColumn.input("qty", "数量", true, "基本单位"));

    private final ForecastService service;

    public SalesForecastController(ForecastService service) {
        this.service = service;
    }

    @GetMapping("/forecasts")
    @PreAuthorize("@ss.has('sales:forecast:query')")
    public CommonResult<PageResult<ForecastRow>> page(@Valid ForecastQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/forecasts/{id}")
    @PreAuthorize("@ss.has('sales:forecast:query')")
    public CommonResult<ForecastDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @GetMapping("/forecasts/{id}/lines/{lineId}/consumptions")
    @PreAuthorize("@ss.has('sales:forecast:query')")
    public CommonResult<List<ConsumptionRow>> consumptions(@PathVariable Long id, @PathVariable Long lineId) {
        return CommonResult.success(service.consumptions(id, lineId));
    }

    @GetMapping("/forecasts/{id}/previous-rows")
    @PreAuthorize("@ss.hasAny('sales:forecast:create', 'sales:forecast:update')")
    public CommonResult<List<RowResp>> previousRows(@PathVariable Long id) {
        return CommonResult.success(service.previousRows(id));
    }

    @PostMapping("/forecasts")
    @PreAuthorize("@ss.has('sales:forecast:create')")
    public CommonResult<Long> create(@Valid @RequestBody ForecastSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/forecasts/{id}")
    @PreAuthorize("@ss.has('sales:forecast:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ForecastSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @DeleteMapping("/forecasts/{id}")
    @PreAuthorize("@ss.has('sales:forecast:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/forecasts/{id}/publish")
    @PreAuthorize("@ss.has('sales:forecast:publish')")
    public CommonResult<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return CommonResult.success();
    }

    @PostMapping("/forecasts/{id}/close")
    @PreAuthorize("@ss.has('sales:forecast:publish')")
    public CommonResult<Void> close(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.close(id, req.reason());
        return CommonResult.success();
    }

    @PostMapping("/forecasts/{id}/copy")
    @PreAuthorize("@ss.has('sales:forecast:create')")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.success(service.copy(id, false));
    }

    /** 修订：复制为新草稿，发布时自动关闭旧版并迁移冲销记录 */
    @PostMapping("/forecasts/{id}/revise")
    @PreAuthorize("@ss.has('sales:forecast:create')")
    public CommonResult<Long> revise(@PathVariable Long id) {
        return CommonResult.success(service.copy(id, true));
    }

    @GetMapping("/forecasts/import-template")
    @PreAuthorize("@ss.hasAny('sales:forecast:create', 'sales:forecast:update')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "销售预测", IMPORT_COLUMNS);
    }

    @PostMapping(value = "/forecasts/{id}/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasAny('sales:forecast:create', 'sales:forecast:update')")
    public CommonResult<ImportCheckResult> importCheck(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report, HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        service.checkImport(id, rows);
        if (report) {
            ExcelSupport.writeBytes(response, "预测导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, r -> r.hasError() ? null : "UPSERT"));
    }

    @PostMapping(value = "/forecasts/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasAny('sales:forecast:create', 'sales:forecast:update')")
    public CommonResult<ImportResult> doImport(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                               @RequestParam(defaultValue = "false") boolean partial) {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        service.checkImport(id, rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0 && !partial) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(service.doImport(id, rows.stream().filter(r -> !r.hasError()).toList()));
    }
}
