package com.erp.module.purchase.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.purchase.controller.vo.ReportVOs.ExecutionQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.ExecutionRow;
import com.erp.module.purchase.controller.vo.ReportVOs.FollowUpReq;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrend;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrendQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.PriceTrendRow;
import com.erp.module.purchase.controller.vo.ReportVOs.SummaryQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.SummaryRow;
import com.erp.module.purchase.controller.vo.ReportVOs.TrackingQuery;
import com.erp.module.purchase.controller.vo.ReportVOs.TrackingRow;
import com.erp.module.purchase.service.PurExportSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** 采购报表（需求 07-11 第 3 节） */
@Tag(name = "资材 - 采购报表")
@RestController
@RequestMapping("/api/purchase/reports")
public class ReportController {

    static final List<ExcelColumn<TrackingRow>> TRACKING_COLUMNS = List.of(
            ExcelColumn.text("orderNo", "订单号", TrackingRow::orderNo),
            ExcelColumn.number("lineNo", "行号", TrackingRow::lineNo),
            ExcelColumn.text("supplierName", "供应商", TrackingRow::supplierName),
            ExcelColumn.text("materialCode", "物料编码", TrackingRow::materialCode),
            ExcelColumn.text("materialName", "名称", TrackingRow::materialName),
            ExcelColumn.text("materialSpec", "规格", TrackingRow::materialSpec),
            ExcelColumn.number("qty", "订购数量", TrackingRow::qty),
            ExcelColumn.number("receivedQty", "已到货", TrackingRow::receivedQty),
            ExcelColumn.number("openQty", "未到货", TrackingRow::openQty),
            ExcelColumn.date("requiredDate", "要求日期", TrackingRow::requiredDate),
            ExcelColumn.date("confirmedDate", "确认交期", TrackingRow::confirmedDate),
            ExcelColumn.number("overdueDays", "逾期天数", TrackingRow::overdueDays),
            ExcelColumn.text("demand", "关联需求", TrackingRow::demand),
            ExcelColumn.text("ownerName", "采购员", TrackingRow::ownerName),
            ExcelColumn.text("lastFollowUp", "最近跟催", TrackingRow::lastFollowUp));

    static final List<ExcelColumn<ExecutionRow>> EXECUTION_COLUMNS = List.of(
            ExcelColumn.text("orderNo", "订单号", ExecutionRow::orderNo),
            ExcelColumn.date("docDate", "日期", ExecutionRow::docDate),
            ExcelColumn.text("supplierName", "供应商", ExecutionRow::supplierName),
            ExcelColumn.number("lineNo", "行号", ExecutionRow::lineNo),
            ExcelColumn.text("materialCode", "物料编码", ExecutionRow::materialCode),
            ExcelColumn.text("materialName", "名称", ExecutionRow::materialName),
            ExcelColumn.number("qty", "订购", ExecutionRow::qty),
            ExcelColumn.number("receivedQty", "到货", ExecutionRow::receivedQty),
            ExcelColumn.number("stockedQty", "入库", ExecutionRow::stockedQty),
            ExcelColumn.number("qualifiedQty", "合格", ExecutionRow::qualifiedQty),
            ExcelColumn.number("returnedQty", "退货", ExecutionRow::returnedQty),
            ExcelColumn.number("statementQty", "对账", ExecutionRow::statementQty),
            ExcelColumn.number("openQty", "未到货", ExecutionRow::openQty),
            ExcelColumn.text("currency", "币别", ExecutionRow::currency),
            ExcelColumn.number("priceInclTax", "含税单价", ExecutionRow::priceInclTax),
            ExcelColumn.number("totalAmount", "金额", ExecutionRow::totalAmount),
            ExcelColumn.text("lineStatus", "行状态", ExecutionRow::lineStatus));

    static final List<ExcelColumn<PriceTrendRow>> TREND_COLUMNS = List.of(
            ExcelColumn.text("month", "月份", PriceTrendRow::month),
            ExcelColumn.text("materialCode", "物料编码", PriceTrendRow::materialCode),
            ExcelColumn.text("materialName", "名称", PriceTrendRow::materialName),
            ExcelColumn.text("supplierName", "供应商", PriceTrendRow::supplierName),
            ExcelColumn.number("qty", "数量", PriceTrendRow::qty),
            ExcelColumn.number("avgPrice", "平均单价", PriceTrendRow::avgPrice),
            ExcelColumn.number("maxPrice", "最高单价", PriceTrendRow::maxPrice),
            ExcelColumn.number("minPrice", "最低单价", PriceTrendRow::minPrice));

    static final List<ExcelColumn<SummaryRow>> SUMMARY_COLUMNS = List.of(
            ExcelColumn.text("label1", "维度 1", SummaryRow::label1),
            ExcelColumn.text("label2", "维度 2", SummaryRow::label2),
            ExcelColumn.number("orderAmount", "下单金额", SummaryRow::orderAmount),
            ExcelColumn.number("receivedAmount", "到货金额", SummaryRow::receivedAmount),
            ExcelColumn.number("qualifiedAmount", "合格金额", SummaryRow::qualifiedAmount),
            ExcelColumn.number("returnAmount", "退货金额", SummaryRow::returnAmount),
            ExcelColumn.number("orderLineCount", "订单行数", SummaryRow::orderLineCount),
            ExcelColumn.number("ontimeRate", "准时率(%)", SummaryRow::ontimeRate),
            ExcelColumn.number("lotPassRate", "批次合格率(%)", SummaryRow::lotPassRate));

    private final ReportService service;
    private final OrderService orderService;
    private final PurExportSupport exportSupport;

    public ReportController(ReportService service, OrderService orderService, PurExportSupport exportSupport) {
        this.service = service;
        this.orderService = orderService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/delivery-tracking")
    @PreAuthorize("@ss.has('pur:report:query')")
    public CommonResult<PageResult<TrackingRow>> tracking(@Valid TrackingQuery q) {
        return CommonResult.success(service.tracking(q));
    }

    @GetMapping("/delivery-tracking/export")
    @PreAuthorize("@ss.has('pur:report:export')")
    public void trackingExport(@Valid TrackingQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "交期跟踪", TRACKING_COLUMNS, q.getColumns(), limit -> limit(service.trackingRows(q), limit));
    }

    @Operation(summary = "记录跟催：更新承诺日期（确认交期）并记入订单操作日志")
    @PostMapping("/delivery-tracking/{orderLineId}/follow-up")
    @PreAuthorize("@ss.hasAny('pur:report:query', 'pur:order:confirm-date')")
    public CommonResult<Void> followUp(@PathVariable Long orderLineId, @RequestBody FollowUpReq req) {
        orderService.followUp(orderLineId, req.content(), req.newDate());
        return CommonResult.success();
    }

    @GetMapping("/order-execution")
    @PreAuthorize("@ss.has('pur:report:query')")
    public CommonResult<PageResult<ExecutionRow>> execution(@Valid ExecutionQuery q) {
        return CommonResult.success(service.execution(q));
    }

    @GetMapping("/order-execution/export")
    @PreAuthorize("@ss.has('pur:report:export')")
    public void executionExport(@Valid ExecutionQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "采购订单执行表", EXECUTION_COLUMNS, q.getColumns(), limit -> limit(service.executionRows(q), limit));
    }

    @GetMapping("/price-trend")
    @PreAuthorize("@ss.has('pur:report:query')")
    public CommonResult<PriceTrend> priceTrend(@Valid PriceTrendQuery q) {
        return CommonResult.success(service.priceTrend(q));
    }

    @GetMapping("/price-trend/export")
    @PreAuthorize("@ss.has('pur:report:export')")
    public void priceTrendExport(@Valid PriceTrendQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "采购价格趋势", TREND_COLUMNS, columns, limit -> limit(service.priceTrend(q).rows(), limit));
    }

    @GetMapping("/summary")
    @PreAuthorize("@ss.has('pur:report:query')")
    public CommonResult<List<SummaryRow>> summary(@Valid SummaryQuery q) {
        return CommonResult.success(service.summary(q));
    }

    @GetMapping("/summary/export")
    @PreAuthorize("@ss.has('pur:report:export')")
    public void summaryExport(@Valid SummaryQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "采购汇总", SUMMARY_COLUMNS, columns, limit -> limit(service.summary(q), limit));
    }

    private static <T> List<T> limit(List<T> list, int limit) {
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
