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
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.production.controller.vo.ReportCenterVOs.Achievement;
import com.erp.module.production.controller.vo.ReportCenterVOs.AchievementQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.DelayedOrder;
import com.erp.module.production.controller.vo.ReportCenterVOs.OutputQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.OutputRow;
import com.erp.module.production.controller.vo.ReportCenterVOs.ProgressQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.ProgressRow;
import com.erp.module.production.controller.vo.ReportCenterVOs.VarianceQuery;
import com.erp.module.production.controller.vo.ReportCenterVOs.VarianceRow;
import com.erp.module.production.service.MfgExportSupport;
import com.erp.module.production.service.center.ReportCenterService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** 生产报表（需求 09-08） */
@Tag(name = "生产 - 生产报表")
@RestController
@RequestMapping("/api/production/reports")
public class ProductionReportCenterController {

    static final List<ExcelColumn<ProgressRow>> PROGRESS_COLUMNS = List.of(
            ExcelColumn.text("prodOrderNo", "生产订单", ProgressRow::prodOrderNo),
            ExcelColumn.text("materialCode", "产品编码", ProgressRow::materialCode),
            ExcelColumn.text("materialName", "产品名称", ProgressRow::materialName),
            ExcelColumn.number("qty", "计划数量", ProgressRow::qty),
            ExcelColumn.text("operations", "各工序合格", r -> r.operations().stream().map(o -> o.seq() + " " + o.operation() + "：" + o.goodQty().stripTrailingZeros()
                    .toPlainString()).collect(Collectors.joining("；"))),
            ExcelColumn.number("completedQty", "完工", ProgressRow::completedQty),
            ExcelColumn.number("stockedQty", "入库", ProgressRow::stockedQty),
            ExcelColumn.date("planEnd", "计划完工", ProgressRow::planEnd),
            ExcelColumn.text("delayed", "预计延期", r -> r.delayed() ? "是" : ""),
            ExcelColumn.text("salesOrderNo", "销售订单", ProgressRow::salesOrderNo),
            ExcelColumn.date("customerDueDate", "客户交期", ProgressRow::customerDueDate));

    static final List<ExcelColumn<VarianceRow>> VARIANCE_COLUMNS = List.of(
            ExcelColumn.text("prodOrderNo", "生产订单", VarianceRow::prodOrderNo),
            ExcelColumn.text("productCode", "产品", VarianceRow::productCode),
            ExcelColumn.text("componentCode", "物料编码", VarianceRow::componentCode),
            ExcelColumn.text("componentName", "物料名称", VarianceRow::componentName),
            ExcelColumn.number("theoreticalQty", "理论用量", VarianceRow::theoreticalQty),
            ExcelColumn.number("netQty", "实际净耗用", VarianceRow::netQty),
            ExcelColumn.number("varianceQty", "差异", VarianceRow::varianceQty),
            ExcelColumn.number("varianceRate", "差异率", VarianceRow::varianceRate),
            ExcelColumn.number("overIssuedQty", "超领", VarianceRow::overIssuedQty),
            ExcelColumn.text("overReasons", "超领原因", VarianceRow::overReasons));

    static final List<ExcelColumn<OutputRow>> OUTPUT_COLUMNS = List.of(
            ExcelColumn.text("label", "维度", OutputRow::label),
            ExcelColumn.number("goodQty", "合格产量", OutputRow::goodQty),
            ExcelColumn.number("scrapQty", "报废", OutputRow::scrapQty),
            ExcelColumn.number("workHours", "实际工时", OutputRow::workHours),
            ExcelColumn.number("stdHours", "标准工时", OutputRow::stdHours),
            ExcelColumn.number("efficiency", "效率", OutputRow::efficiency),
            ExcelColumn.number("perCapita", "人均产量", OutputRow::perCapita));

    static final List<ExcelColumn<DelayedOrder>> DELAY_COLUMNS = List.of(
            ExcelColumn.text("prodOrderNo", "生产订单", DelayedOrder::prodOrderNo),
            ExcelColumn.text("materialCode", "产品编码", DelayedOrder::materialCode),
            ExcelColumn.text("materialName", "产品名称", DelayedOrder::materialName),
            ExcelColumn.date("planEnd", "计划完工", DelayedOrder::planEnd),
            ExcelColumn.date("actualEnd", "实际完工", DelayedOrder::actualEnd),
            ExcelColumn.number("delayDays", "延期天数", DelayedOrder::delayDays));

    private final ReportCenterService service;
    private final MfgExportSupport exportSupport;

    public ProductionReportCenterController(ReportCenterService service, MfgExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/progress")
    @PreAuthorize("@ss.has('mfg:report-center:query')")
    public CommonResult<PageResult<ProgressRow>> progress(@Valid ProgressQuery q) {
        return CommonResult.success(service.progress(q));
    }

    @GetMapping("/progress/export")
    @PreAuthorize("@ss.has('mfg:report-center:export')")
    public void exportProgress(@Valid ProgressQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "生产订单进度", PROGRESS_COLUMNS, q.getColumns(), limit -> service.progressAll(q, limit));
    }

    @GetMapping("/material-variance")
    @PreAuthorize("@ss.has('mfg:report-center:query')")
    public CommonResult<List<VarianceRow>> variance(VarianceQuery q) {
        return CommonResult.success(service.variance(q));
    }

    @GetMapping("/material-variance/export")
    @PreAuthorize("@ss.has('mfg:report-center:export')")
    public void exportVariance(VarianceQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "领料差异分析", VARIANCE_COLUMNS, null, limit -> service.variance(q).stream().limit(limit).toList());
    }

    @GetMapping("/output-hours")
    @PreAuthorize("@ss.has('mfg:report-center:query')")
    public CommonResult<List<OutputRow>> output(OutputQuery q) {
        return CommonResult.success(service.output(q));
    }

    @GetMapping("/output-hours/export")
    @PreAuthorize("@ss.has('mfg:report-center:export')")
    public void exportOutput(OutputQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "产量与工时", OUTPUT_COLUMNS, null, limit -> service.output(q).stream().limit(limit).toList());
    }

    @GetMapping("/plan-achievement")
    @PreAuthorize("@ss.has('mfg:report-center:query')")
    public CommonResult<Achievement> achievement(AchievementQuery q) {
        return CommonResult.success(service.achievement(q));
    }

    @GetMapping("/plan-achievement/export")
    @PreAuthorize("@ss.has('mfg:report-center:export')")
    public void exportAchievement(AchievementQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "计划达成（延期订单）", DELAY_COLUMNS, null, limit -> service.achievement(q).delayed().stream().limit(limit).toList());
    }
}
