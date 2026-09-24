package com.erp.module.system.controller;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportCheckResult;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.operlog.OperLog;
import com.erp.module.system.api.currency.RateType;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencyResp;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencySave;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencySimple;
import com.erp.module.system.controller.vo.CurrencyVOs.RateBatch;
import com.erp.module.system.controller.vo.CurrencyVOs.RateLookup;
import com.erp.module.system.controller.vo.CurrencyVOs.RateQuery;
import com.erp.module.system.controller.vo.CurrencyVOs.RateResp;
import com.erp.module.system.controller.vo.CurrencyVOs.RateSave;
import com.erp.module.system.service.CurrencyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 币别与汇率（01-07） */
@Tag(name = "系统管理 - 币别汇率")
@RestController
@RequestMapping("/api/system")
public class CurrencyController {

    private static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("currency", "币别", true, "3 位代码，如 USD"),
            ExcelColumn.<Object>input("rateType", "汇率类型", true, "日汇率 / 月末汇率").options(List.of("日汇率", "月末汇率")),
            ExcelColumn.<Object>input("effectiveDate", "生效日期", true, "yyyy-MM-dd；月末汇率自动调整为当月最后一天").ofType(ExcelColumn.Type.DATE),
            ExcelColumn.input("rate", "汇率", true, "1 外币 = 汇率 × 本位币，最多 6 位小数"));

    private static final List<ExcelColumn<RateResp>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("currency", "币别", RateResp::currency),
            ExcelColumn.<RateResp>text("rateType", "汇率类型", r -> "DAILY".equals(r.rateType()) ? "日汇率" : "月末汇率"),
            ExcelColumn.date("effectiveDate", "生效日期", RateResp::effectiveDate),
            ExcelColumn.number("rate", "汇率", RateResp::rate),
            ExcelColumn.<RateResp>text("source", "来源", r -> "IMPORT".equals(r.source()) ? "导入" : "手工"),
            ExcelColumn.text("remark", "说明", RateResp::remark),
            ExcelColumn.text("updatedByName", "修改人", RateResp::updatedByName),
            ExcelColumn.dateTime("updatedAt", "修改时间", RateResp::updatedAt));

    private final CurrencyService currencyService;
    private final ParamApi paramApi;

    public CurrencyController(CurrencyService currencyService, ParamApi paramApi) {
        this.currencyService = currencyService;
        this.paramApi = paramApi;
    }

    // ==================== 币别 ====================

    @GetMapping("/currencies")
    @PreAuthorize("@ss.has('system:currency:query')")
    public CommonResult<List<CurrencyResp>> list() {
        return CommonResult.success(currencyService.list());
    }

    /** 启用币别，登录即可（CurrencySelect） */
    @GetMapping("/currencies/simple")
    public CommonResult<List<CurrencySimple>> simple() {
        return CommonResult.success(currencyService.simple());
    }

    @OperLog("新建币别")
    @PostMapping("/currencies")
    @PreAuthorize("@ss.has('system:currency:create')")
    public CommonResult<Long> create(@Valid @RequestBody CurrencySave req) {
        return CommonResult.success(currencyService.create(req));
    }

    @OperLog("修改币别")
    @PutMapping("/currencies/{id}")
    @PreAuthorize("@ss.has('system:currency:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CurrencySave req) {
        currencyService.update(id, req);
        return CommonResult.success();
    }

    @OperLog("启用币别")
    @PostMapping("/currencies/{id}/enable")
    @PreAuthorize("@ss.has('system:currency:update')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        currencyService.changeStatus(id, EnableStatus.ENABLED);
        return CommonResult.success();
    }

    @OperLog("停用币别")
    @PostMapping("/currencies/{id}/disable")
    @PreAuthorize("@ss.has('system:currency:update')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        currencyService.changeStatus(id, EnableStatus.DISABLED);
        return CommonResult.success();
    }

    @OperLog("设置本位币")
    @PostMapping("/currencies/{id}/set-base")
    @PreAuthorize("@ss.has('system:currency:set-base')")
    public CommonResult<Void> setBase(@PathVariable Long id) {
        currencyService.setBase(id);
        return CommonResult.success();
    }

    // ==================== 汇率 ====================

    @GetMapping("/exchange-rates")
    @PreAuthorize("@ss.has('system:currency:query')")
    public CommonResult<PageResult<RateResp>> rates(@Valid RateQuery q) {
        return CommonResult.success(currencyService.pageRates(q));
    }

    /** 单据带出汇率，登录即可 */
    @GetMapping("/exchange-rates/lookup")
    public CommonResult<RateLookup> lookup(@RequestParam String currency,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(defaultValue = "DAILY") RateType type) {
        return CommonResult.success(currencyService.lookup(currency, date, type));
    }

    @OperLog("新建汇率")
    @PostMapping("/exchange-rates")
    @PreAuthorize("@ss.has('system:rate:create')")
    public CommonResult<Long> createRate(@Valid @RequestBody RateSave req) {
        return CommonResult.success(currencyService.createRate(req));
    }

    @OperLog("修改汇率")
    @PutMapping("/exchange-rates/{id}")
    @PreAuthorize("@ss.has('system:rate:update')")
    public CommonResult<Void> updateRate(@PathVariable Long id, @Valid @RequestBody RateSave req) {
        currencyService.updateRate(id, req);
        return CommonResult.success();
    }

    @OperLog("删除汇率")
    @DeleteMapping("/exchange-rates/{id}")
    @PreAuthorize("@ss.has('system:rate:delete')")
    public CommonResult<Void> deleteRate(@PathVariable Long id) {
        currencyService.deleteRate(id);
        return CommonResult.success();
    }

    @OperLog("批量录入汇率")
    @PostMapping("/exchange-rates/batch")
    @PreAuthorize("@ss.has('system:rate:create')")
    public CommonResult<Integer> batch(@Valid @RequestBody RateBatch req) {
        return CommonResult.success(currencyService.batch(req));
    }

    @GetMapping("/exchange-rates/import-template")
    @PreAuthorize("@ss.has('system:rate:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.template(response, "汇率", IMPORT_COLUMNS);
    }

    @PostMapping(value = "/exchange-rates/import/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('system:rate:import')")
    public CommonResult<ImportCheckResult> importCheck(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean report,
                                                       HttpServletResponse response) throws IOException {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        currencyService.checkImport(rows);
        if (report) {
            ExcelSupport.writeBytes(response, "汇率导入错误报告.xlsx", ExcelSupport.errorReport(file, rows));
            return null;
        }
        return CommonResult.success(ImportCheckResult.of(IMPORT_COLUMNS, rows, currencyService::importAction));
    }

    /** 已存在同键记录时覆盖；不允许部分导入 */
    @OperLog("导入汇率")
    @PostMapping(value = "/exchange-rates/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.has('system:rate:import')")
    public CommonResult<ImportResult> doImport(@RequestPart("file") MultipartFile file) {
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        currencyService.checkImport(rows);
        long errors = rows.stream().filter(ImportRow::hasError).count();
        if (errors > 0) throw BizException.of(GlobalErrorCodes.IMPORT_HAS_ERRORS, errors);
        return CommonResult.success(new ImportResult(currencyService.importRates(rows), 0, List.of()));
    }

    @GetMapping("/exchange-rates/export")
    @PreAuthorize("@ss.has('system:rate:export')")
    public void export(@Valid RateQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        int max = paramApi.getInt("sys.export.sync-max-rows");
        q.setPageNo(1);
        q.setPageSize(500);
        List<RateResp> all = new ArrayList<>();
        while (all.size() < max) {
            PageResult<RateResp> p = currencyService.pageRates(q);
            all.addAll(p.list());
            if (p.list().isEmpty() || all.size() >= p.total()) break;
            q.setPageNo(q.getPageNo() + 1);
        }
        ExcelSupport.export(response, "汇率", EXPORT_COLUMNS, all.size() > max ? all.subList(0, max) : all,
                columns == null ? null : Arrays.asList(columns.split(",")));
    }
}
