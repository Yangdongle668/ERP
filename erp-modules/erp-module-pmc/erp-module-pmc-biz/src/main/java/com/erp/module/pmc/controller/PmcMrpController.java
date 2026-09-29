package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.pmc.controller.vo.CommonVOs.BatchResult;
import com.erp.module.pmc.controller.vo.CommonVOs.IdsReq;
import com.erp.module.pmc.controller.vo.MrpVOs.Balance;
import com.erp.module.pmc.controller.vo.MrpVOs.ConvertReq;
import com.erp.module.pmc.controller.vo.MrpVOs.ExceptionQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.ExceptionRow;
import com.erp.module.pmc.controller.vo.MrpVOs.IgnoreReq;
import com.erp.module.pmc.controller.vo.MrpVOs.PegRow;
import com.erp.module.pmc.controller.vo.MrpVOs.RunQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.RunReq;
import com.erp.module.pmc.controller.vo.MrpVOs.RunRow;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionQuery;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionRow;
import com.erp.module.pmc.controller.vo.MrpVOs.SuggestionUpdate;
import com.erp.module.pmc.service.PmcExportSupport;
import com.erp.module.pmc.service.mrp.MrpRunService;
import com.erp.module.pmc.service.mrp.SuggestionService;
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
import java.util.List;

@Tag(name = "PMC - MRP")
@RestController
@RequestMapping("/api/pmc/mrp")
public class PmcMrpController {

    static final List<ExcelColumn<SuggestionRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("type", "类型", SuggestionRow::type),
            ExcelColumn.text("materialCode", "物料编码", SuggestionRow::materialCode),
            ExcelColumn.text("materialName", "名称", SuggestionRow::materialName),
            ExcelColumn.text("materialSpec", "规格", SuggestionRow::materialSpec),
            ExcelColumn.text("baseUom", "单位", SuggestionRow::baseUom),
            ExcelColumn.number("qty", "建议数量", SuggestionRow::qty),
            ExcelColumn.number("netRequirement", "净需求", SuggestionRow::netRequirement),
            ExcelColumn.date("requiredDate", "需求日期", SuggestionRow::requiredDate),
            ExcelColumn.date("releaseDate", "建议下达", SuggestionRow::releaseDate),
            ExcelColumn.text("supplierName", "供应商", SuggestionRow::supplierName),
            ExcelColumn.text("sourceSummary", "需求来源", SuggestionRow::sourceSummary),
            ExcelColumn.text("plannerName", "计划员", SuggestionRow::plannerName),
            ExcelColumn.text("buyerName", "采购员", SuggestionRow::buyerName),
            ExcelColumn.text("status", "状态", SuggestionRow::status));

    private final MrpRunService runService;
    private final SuggestionService suggestionService;
    private final PmcExportSupport exportSupport;

    public PmcMrpController(MrpRunService runService, SuggestionService suggestionService, PmcExportSupport exportSupport) {
        this.runService = runService;
        this.suggestionService = suggestionService;
        this.exportSupport = exportSupport;
    }

    @PostMapping("/runs")
    @PreAuthorize("@ss.has('pmc:mrp:run')")
    public CommonResult<Long> run(@RequestBody(required = false) RunReq req) {
        return CommonResult.success(runService.start(req));
    }

    @GetMapping("/runs")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<PageResult<RunRow>> runs(@Valid RunQuery q) {
        return CommonResult.success(runService.page(q));
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<RunRow> runDetail(@PathVariable Long id) {
        return CommonResult.success(runService.get(id));
    }

    @GetMapping("/balance")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<Balance> balance(@RequestParam Long materialId, @RequestParam(required = false) Long runId) {
        return CommonResult.success(suggestionService.balance(materialId, runId));
    }

    @GetMapping("/suggestions")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<PageResult<SuggestionRow>> suggestions(@Valid SuggestionQuery q) {
        return CommonResult.success(suggestionService.page(q));
    }

    @GetMapping("/suggestions/export")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public void export(SuggestionQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "MRP 建议", EXPORT_COLUMNS, null, limit -> {
            q.setPageNo(1);
            q.setPageSize(limit);
            return suggestionService.page(q).list();
        });
    }

    @PutMapping("/suggestions/{id}")
    @PreAuthorize("@ss.hasAny('pmc:mrp:convert', 'pmc:mrp:run')")
    public CommonResult<List<String>> update(@PathVariable Long id, @RequestBody SuggestionUpdate req) {
        return CommonResult.success(suggestionService.update(id, req));
    }

    @PostMapping("/suggestions/convert")
    @PreAuthorize("@ss.has('pmc:mrp:convert')")
    public CommonResult<BatchResult> convert(@RequestBody ConvertReq req) {
        return CommonResult.success(suggestionService.convert(req.ids(), Boolean.TRUE.equals(req.release())));
    }

    @PostMapping("/suggestions/ignore")
    @PreAuthorize("@ss.has('pmc:mrp:ignore')")
    public CommonResult<Integer> ignore(@Valid @RequestBody IgnoreReq req) {
        return CommonResult.success(suggestionService.ignore(req.ids(), req.reason()));
    }

    @GetMapping("/suggestions/{id}/pegging")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<List<PegRow>> pegging(@PathVariable Long id) {
        return CommonResult.success(suggestionService.pegging(id));
    }

    @GetMapping("/exceptions")
    @PreAuthorize("@ss.has('pmc:mrp:query')")
    public CommonResult<PageResult<ExceptionRow>> exceptions(@Valid ExceptionQuery q) {
        return CommonResult.success(suggestionService.exceptions(q));
    }

    @PostMapping("/exceptions/push")
    @PreAuthorize("@ss.has('pmc:mrp:convert')")
    public CommonResult<BatchResult> push(@RequestBody IdsReq req) {
        return CommonResult.success(suggestionService.push(req.ids()));
    }

    @PostMapping("/exceptions/{id}/handled")
    @PreAuthorize("@ss.hasAny('pmc:mrp:convert', 'pmc:mrp:query')")
    public CommonResult<Void> handled(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean handled) {
        suggestionService.handled(id, handled);
        return CommonResult.success();
    }
}
