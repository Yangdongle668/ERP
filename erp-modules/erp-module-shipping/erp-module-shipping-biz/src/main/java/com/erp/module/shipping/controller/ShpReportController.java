package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.shipping.controller.vo.ReportVOs.DelayRow;
import com.erp.module.shipping.controller.vo.ReportVOs.DetailReport;
import com.erp.module.shipping.controller.vo.ReportVOs.DetailRow;
import com.erp.module.shipping.controller.vo.ReportVOs.ExportStatRow;
import com.erp.module.shipping.controller.vo.ReportVOs.OnTimeReport;
import com.erp.module.shipping.controller.vo.ReportVOs.PendingRow;
import com.erp.module.shipping.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.ShpExportSupport;
import com.erp.module.shipping.service.report.ShippingReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "出货 - 报表")
@RestController
@RequestMapping("/api/shipping/reports")
public class ShpReportController {

    static final List<ExcelColumn<PendingRow>> PENDING_COLUMNS = List.of(
            ExcelColumn.text("customerName", "客户", PendingRow::customerName),
            ExcelColumn.text("orderNo", "订单号", PendingRow::orderNo),
            ExcelColumn.number("lineNo", "行号", PendingRow::lineNo),
            ExcelColumn.text("materialCode", "物料编码", PendingRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", PendingRow::materialName),
            ExcelColumn.number("qty", "数量", PendingRow::qty),
            ExcelColumn.date("dueDate", "交期", PendingRow::dueDate),
            ExcelColumn.text("noticeNo", "出货通知", r -> r.noticeNo() == null ? "未通知" : r.noticeNo()),
            ExcelColumn.text("noticeStatus", "通知状态", r -> r.noticeStatus() == null ? "" : NoticeStatus.valueOf(r.noticeStatus()).label()),
            ExcelColumn.number("availableQty", "可用库存", PendingRow::availableQty),
            ExcelColumn.text("ownerName", "业务员", PendingRow::ownerName));

    static final List<ExcelColumn<DetailRow>> DETAIL_COLUMNS = List.of(
            ExcelColumn.date("shipDate", "出货日期", DetailRow::shipDate),
            ExcelColumn.text("shipmentNo", "出货单", DetailRow::shipmentNo),
            ExcelColumn.text("customerName", "客户", DetailRow::customerName),
            ExcelColumn.text("orderNo", "订单号", DetailRow::orderNo),
            ExcelColumn.text("customerPoNo", "客户 PO", DetailRow::customerPoNo),
            ExcelColumn.text("materialCode", "物料编码", DetailRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", DetailRow::materialName),
            ExcelColumn.text("batchNo", "批次", DetailRow::batchNo),
            ExcelColumn.number("qty", "数量", DetailRow::qty),
            ExcelColumn.text("currency", "币别", DetailRow::currency),
            ExcelColumn.number("price", "单价", DetailRow::price),
            ExcelColumn.number("amount", "金额", DetailRow::amount),
            ExcelColumn.number("amountBase", "本位币金额", DetailRow::amountBase),
            ExcelColumn.text("transportMode", "运输方式", DetailRow::transportMode),
            ExcelColumn.text("blNo", "提单号", DetailRow::blNo),
            ExcelColumn.text("ownerName", "业务员", DetailRow::ownerName));

    static final List<ExcelColumn<DelayRow>> DELAY_COLUMNS = List.of(
            ExcelColumn.text("orderNo", "订单号", DelayRow::orderNo),
            ExcelColumn.number("lineNo", "行号", DelayRow::lineNo),
            ExcelColumn.text("customerName", "客户", DelayRow::customerName),
            ExcelColumn.text("materialCode", "物料编码", DelayRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", DelayRow::materialName),
            ExcelColumn.date("dueDate", "交期", DelayRow::dueDate),
            ExcelColumn.date("firstShipDate", "首次出货", DelayRow::firstShipDate),
            ExcelColumn.number("delayDays", "延期天数", DelayRow::delayDays),
            ExcelColumn.text("ownerName", "业务员", DelayRow::ownerName));

    static final List<ExcelColumn<ExportStatRow>> STAT_COLUMNS = List.of(
            ExcelColumn.text("month", "月份", ExportStatRow::month),
            ExcelColumn.text("country", "目的国", ExportStatRow::country),
            ExcelColumn.text("hsCode", "HS 编码", ExportStatRow::hsCode),
            ExcelColumn.number("shipments", "出货单数", ExportStatRow::shipments),
            ExcelColumn.number("qty", "数量", ExportStatRow::qty),
            ExcelColumn.number("amountBase", "金额（本位币）", ExportStatRow::amountBase));

    private final ShippingReportService service;
    private final ShpExportSupport exportSupport;

    public ShpReportController(ShippingReportService service, ShpExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/pending")
    @PreAuthorize("@ss.has('shp:report:query')")
    public CommonResult<List<PendingRow>> pending(ReportQuery q) {
        return CommonResult.success(service.pending(q));
    }

    @GetMapping("/pending/export")
    @PreAuthorize("@ss.has('shp:report:export')")
    public void pendingExport(ReportQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "待出货清单", PENDING_COLUMNS, columns, limit -> service.pending(q).stream().limit(limit).toList());
    }

    @GetMapping("/details")
    @PreAuthorize("@ss.has('shp:report:query')")
    public CommonResult<DetailReport> details(ReportQuery q) {
        return CommonResult.success(service.details(q));
    }

    @GetMapping("/details/export")
    @PreAuthorize("@ss.has('shp:report:export')")
    public void detailsExport(ReportQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "出货明细", DETAIL_COLUMNS, columns, limit -> service.details(q).rows().stream().limit(limit).toList());
    }

    @GetMapping("/on-time")
    @PreAuthorize("@ss.has('shp:report:query')")
    public CommonResult<OnTimeReport> onTime(ReportQuery q) {
        return CommonResult.success(service.onTime(q));
    }

    @GetMapping("/on-time/export")
    @PreAuthorize("@ss.has('shp:report:export')")
    public void onTimeExport(ReportQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "出货延期明细", DELAY_COLUMNS, columns, limit -> service.onTime(q).delays().stream().limit(limit).toList());
    }

    @GetMapping("/export-stats")
    @PreAuthorize("@ss.has('shp:report:query')")
    public CommonResult<List<ExportStatRow>> exportStats(ReportQuery q) {
        return CommonResult.success(service.exportStats(q));
    }

    @GetMapping("/export-stats/export")
    @PreAuthorize("@ss.has('shp:report:export')")
    public void exportStatsExport(ReportQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "出口统计", STAT_COLUMNS, columns, limit -> service.exportStats(q).stream().limit(limit).toList());
    }
}
