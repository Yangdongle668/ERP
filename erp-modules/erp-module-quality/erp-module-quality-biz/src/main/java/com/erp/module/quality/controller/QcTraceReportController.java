package com.erp.module.quality.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.quality.controller.vo.ReportVOs.BatchStock;
import com.erp.module.quality.controller.vo.ReportVOs.FreezeReq;
import com.erp.module.quality.controller.vo.ReportVOs.FreezeResult;
import com.erp.module.quality.controller.vo.ReportVOs.IqcReport;
import com.erp.module.quality.controller.vo.ReportVOs.LotRow;
import com.erp.module.quality.controller.vo.ReportVOs.NcrCapaComplaintReport;
import com.erp.module.quality.controller.vo.ReportVOs.OutgoingReport;
import com.erp.module.quality.controller.vo.ReportVOs.ParetoRow;
import com.erp.module.quality.controller.vo.ReportVOs.ProcessReport;
import com.erp.module.quality.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.quality.controller.vo.ReportVOs.TraceResult;
import com.erp.module.quality.service.QcExportSupport;
import com.erp.module.quality.service.report.QualityReportService;
import com.erp.module.quality.service.report.TraceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "品质 - 质量追溯与报表")
@RestController
@RequestMapping("/api/quality")
public class QcTraceReportController {

    static final List<ExcelColumn<LotRow>> LOT_COLUMNS = List.of(
            ExcelColumn.text("key", "编码", LotRow::key), ExcelColumn.text("name", "名称", LotRow::name),
            ExcelColumn.number("lots", "检验批次", LotRow::lots), ExcelColumn.number("qualifiedLots", "合格批次", LotRow::qualifiedLots),
            ExcelColumn.number("passRate", "批次合格率(%)", LotRow::passRate), ExcelColumn.number("concessionLots", "特采批次", LotRow::concessionLots),
            ExcelColumn.number("rejectedLots", "拒收批次", LotRow::rejectedLots), ExcelColumn.number("sortedLots", "挑选批次", LotRow::sortedLots),
            ExcelColumn.number("inspectedQty", "检验数量", LotRow::inspectedQty), ExcelColumn.number("defectRate", "不良率(%)", LotRow::defectRate));
    static final List<ExcelColumn<ParetoRow>> PARETO_COLUMNS = List.of(
            ExcelColumn.text("code", "缺陷代码", ParetoRow::code), ExcelColumn.text("name", "名称", ParetoRow::name),
            ExcelColumn.number("qty", "数量", ParetoRow::qty), ExcelColumn.number("pct", "占比(%)", ParetoRow::pct),
            ExcelColumn.number("cumulativePct", "累计占比(%)", ParetoRow::cumulativePct));
    static final List<ExcelColumn<BatchStock>> RECALL_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", BatchStock::materialCode), ExcelColumn.text("materialName", "物料名称", BatchStock::materialName),
            ExcelColumn.text("batchNo", "批次", BatchStock::batchNo), ExcelColumn.number("onHandQty", "在库数量", BatchStock::onHandQty),
            ExcelColumn.text("warehouses", "库存分布", b -> String.join("；", b.warehouses().stream().map(w -> w.warehouseName() + " " + w.qty().stripTrailingZeros().toPlainString()).toList())),
            ExcelColumn.number("shippedQty", "已出货数量", BatchStock::shippedQty),
            ExcelColumn.text("shipments", "出货单据", b -> String.join("；", b.shipments().stream().map(s -> s.docNo() + (s.sourceNo() == null ? "" : "（" + s.sourceNo() + "）")).toList())));

    private final TraceService traceService;
    private final QualityReportService reportService;
    private final QcExportSupport exportSupport;

    public QcTraceReportController(TraceService traceService, QualityReportService reportService, QcExportSupport exportSupport) {
        this.traceService = traceService;
        this.reportService = reportService;
        this.exportSupport = exportSupport;
    }

    // ==================== 追溯 ====================

    @GetMapping("/trace/backward")
    @PreAuthorize("@ss.has('qc:trace:query')")
    public CommonResult<TraceResult> backward(@RequestParam Long materialId, @RequestParam String batchNo) {
        return CommonResult.success(traceService.backward(materialId, batchNo));
    }

    @GetMapping("/trace/forward")
    @PreAuthorize("@ss.has('qc:trace:query')")
    public CommonResult<TraceResult> forward(@RequestParam Long materialId, @RequestParam String batchNo) {
        return CommonResult.success(traceService.forward(materialId, batchNo));
    }

    /** 召回清单导出 */
    @GetMapping("/trace/forward/export")
    @PreAuthorize("@ss.has('qc:trace:query')")
    public void recallExport(@RequestParam Long materialId, @RequestParam String batchNo, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "召回清单", RECALL_COLUMNS, null, limit -> traceService.forward(materialId, batchNo).affected());
    }

    @PostMapping("/trace/freeze")
    @PreAuthorize("@ss.has('qc:ncr:create')")
    public CommonResult<FreezeResult> freeze(@RequestBody FreezeReq req) {
        return CommonResult.success(traceService.freeze(req));
    }

    // ==================== 报表 ====================

    @GetMapping("/reports/iqc")
    @PreAuthorize("@ss.has('qc:report:query')")
    public CommonResult<IqcReport> iqc(ReportQuery q) {
        return CommonResult.success(reportService.iqc(q));
    }

    @GetMapping("/reports/iqc/export")
    @PreAuthorize("@ss.has('qc:report:export')")
    public void iqcExport(ReportQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "来料质量-供应商", LOT_COLUMNS, null, limit -> reportService.iqc(q).bySupplier());
    }

    @GetMapping("/reports/process")
    @PreAuthorize("@ss.has('qc:report:query')")
    public CommonResult<ProcessReport> process(ReportQuery q) {
        return CommonResult.success(reportService.process(q));
    }

    @GetMapping("/reports/process/export")
    @PreAuthorize("@ss.has('qc:report:export')")
    public void processExport(ReportQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "制程质量-物料", LOT_COLUMNS, null, limit -> reportService.process(q).byMaterial());
    }

    @GetMapping("/reports/outgoing")
    @PreAuthorize("@ss.has('qc:report:query')")
    public CommonResult<OutgoingReport> outgoing(ReportQuery q) {
        return CommonResult.success(reportService.outgoing(q));
    }

    @GetMapping("/reports/outgoing/export")
    @PreAuthorize("@ss.has('qc:report:export')")
    public void outgoingExport(ReportQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "OQC 月度", LOT_COLUMNS, null, limit -> reportService.outgoing(q).oqcByMonth());
    }

    @GetMapping("/reports/ncr-capa-complaint")
    @PreAuthorize("@ss.has('qc:report:query')")
    public CommonResult<NcrCapaComplaintReport> ncrCapaComplaint(ReportQuery q) {
        return CommonResult.success(reportService.ncrCapaComplaint(q));
    }

    @GetMapping("/reports/defect-pareto")
    @PreAuthorize("@ss.has('qc:report:query')")
    public CommonResult<List<ParetoRow>> pareto(ReportQuery q) {
        return CommonResult.success(reportService.pareto(q));
    }

    @GetMapping("/reports/defect-pareto/export")
    @PreAuthorize("@ss.has('qc:report:export')")
    public void paretoExport(ReportQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "缺陷 Pareto", PARETO_COLUMNS, null, limit -> reportService.pareto(q));
    }
}
