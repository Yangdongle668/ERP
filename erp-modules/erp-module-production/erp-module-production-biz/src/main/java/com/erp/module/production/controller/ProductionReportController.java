package com.erp.module.production.controller;

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
import com.erp.module.production.controller.vo.CommonVOs.BatchResult;
import com.erp.module.production.controller.vo.CommonVOs.IdsReq;
import com.erp.module.production.controller.vo.ReportVOs.Context;
import com.erp.module.production.controller.vo.ReportVOs.DefectQuery;
import com.erp.module.production.controller.vo.ReportVOs.DefectRow;
import com.erp.module.production.controller.vo.ReportVOs.RepairReq;
import com.erp.module.production.controller.vo.ReportVOs.ReportDetail;
import com.erp.module.production.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.production.controller.vo.ReportVOs.ReportRow;
import com.erp.module.production.controller.vo.ReportVOs.ReportSave;
import com.erp.module.production.controller.vo.ReportVOs.SaveResult;
import com.erp.module.production.controller.vo.ReportVOs.ScrapReq;
import com.erp.module.production.controller.vo.ReportVOs.YieldQuery;
import com.erp.module.production.controller.vo.ReportVOs.YieldReport;
import com.erp.module.production.controller.vo.ReportVOs.YieldRow;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.module.production.service.report.DefectService;
import com.erp.module.production.service.report.ReportService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** 报工（需求 09-04）、不良与良率（09-06） */
@Tag(name = "生产 - 报工与不良")
@RestController
@RequestMapping("/api/production")
public class ProductionReportController {

    static final List<ExcelColumn<YieldRow>> YIELD_COLUMNS = List.of(
            ExcelColumn.text("label", "维度", YieldRow::label),
            ExcelColumn.number("inputQty", "投入", YieldRow::inputQty),
            ExcelColumn.number("goodQty", "一次合格", YieldRow::goodQty),
            ExcelColumn.number("defectQty", "不良", YieldRow::defectQty),
            ExcelColumn.number("scrapQty", "报废", YieldRow::scrapQty),
            ExcelColumn.number("repairedQty", "返修合格", YieldRow::repairedQty),
            ExcelColumn.number("firstYield", "一次良率", YieldRow::firstYield),
            ExcelColumn.number("finalYield", "最终良率", YieldRow::finalYield));

    private final ReportService service;
    private final DefectService defectService;

    public ProductionReportController(ReportService service, DefectService defectService) {
        this.service = service;
        this.defectService = defectService;
    }

    @GetMapping("/reports")
    @PreAuthorize("@ss.has('mfg:report:query')")
    public CommonResult<PageResult<ReportRow>> page(@Valid ReportQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/reports/context")
    @PreAuthorize("@ss.hasAny('mfg:report:create', 'mfg:report:query')")
    public CommonResult<Context> context(@RequestParam(required = false) String barcode, @RequestParam(required = false) Long prodOrderId,
                                         @RequestParam(required = false) Integer seq, @RequestParam(required = false) Long workOrderId) {
        return CommonResult.success(barcode != null && !barcode.isBlank() ? service.contextByBarcode(barcode) : service.context(prodOrderId, seq, workOrderId));
    }

    @GetMapping("/reports/{id}")
    @PreAuthorize("@ss.has('mfg:report:query')")
    public CommonResult<ReportDetail> detail(@PathVariable Long id) {
        return CommonResult.success(service.detail(id));
    }

    @PostMapping("/reports")
    @PreAuthorize("@ss.has('mfg:report:create')")
    public CommonResult<SaveResult> create(@Valid @RequestBody ReportSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/reports/{id}")
    @PreAuthorize("@ss.has('mfg:report:update')")
    public CommonResult<SaveResult> update(@PathVariable Long id, @Valid @RequestBody ReportSave req) {
        return CommonResult.success(service.update(id, req));
    }

    @DeleteMapping("/reports/{id}")
    @PreAuthorize("@ss.has('mfg:report:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @PostMapping("/reports/{id}/approve")
    @PreAuthorize("@ss.has('mfg:report:approve')")
    public CommonResult<String> approve(@PathVariable Long id) {
        return CommonResult.success(service.approve(id));
    }

    @PostMapping("/reports/batch-approve")
    @PreAuthorize("@ss.has('mfg:report:approve')")
    public CommonResult<BatchResult> batchApprove(@RequestBody IdsReq req) {
        return CommonResult.success(service.batchApprove(req.ids()));
    }

    @PostMapping("/reports/{id}/unapprove")
    @PreAuthorize("@ss.has('mfg:report:unapprove')")
    public CommonResult<Void> unapprove(@PathVariable Long id) {
        service.unapprove(id);
        return CommonResult.success();
    }

    // ==================== 不良 ====================

    @GetMapping("/defects")
    @PreAuthorize("@ss.has('mfg:defect:query')")
    public CommonResult<PageResult<DefectRow>> defects(@Valid DefectQuery q) {
        return CommonResult.success(defectService.page(q));
    }

    @GetMapping("/defects/ncr-available")
    @PreAuthorize("@ss.has('mfg:defect:query')")
    public CommonResult<Boolean> ncrAvailable() {
        return CommonResult.success(defectService.ncrAvailable());
    }

    @PostMapping("/defects/{id}/repair")
    @PreAuthorize("@ss.has('mfg:defect:update')")
    public CommonResult<Void> repair(@PathVariable Long id, @Valid @RequestBody RepairReq req) {
        defectService.repair(id, req.qty());
        return CommonResult.success();
    }

    @PostMapping("/defects/{id}/scrap")
    @PreAuthorize("@ss.has('mfg:defect:update')")
    public CommonResult<Void> scrap(@PathVariable Long id, @Valid @RequestBody ScrapReq req) {
        defectService.scrap(id, req.qty(), req.scrapReason());
        return CommonResult.success();
    }

    @PostMapping("/defects/{id}/to-ncr")
    @PreAuthorize("@ss.has('mfg:defect:to-ncr')")
    public CommonResult<String> toNcr(@PathVariable Long id) {
        return CommonResult.success(defectService.toNcr(id));
    }

    // ==================== 良率 ====================

    @GetMapping("/reports/yield")
    @PreAuthorize("@ss.has('mfg:defect:query')")
    public CommonResult<YieldReport> yield(YieldQuery q) {
        return CommonResult.success(defectService.yield(q));
    }

    @GetMapping("/reports/yield/export")
    @PreAuthorize("@ss.has('mfg:defect:query')")
    public void exportYield(YieldQuery q, HttpServletResponse response) throws IOException {
        ExcelSupport.export(response, "良率报表", YIELD_COLUMNS, defectService.yield(q).rows(), null);
    }
}
