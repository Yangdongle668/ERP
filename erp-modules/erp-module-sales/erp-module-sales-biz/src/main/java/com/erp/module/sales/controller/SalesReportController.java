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
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineRow;
import com.erp.module.sales.controller.vo.OrderVOs.OrderQuery;
import com.erp.module.sales.controller.vo.ReportVOs.CustomerRankRow;
import com.erp.module.sales.controller.vo.ReportVOs.OpenOrderQuery;
import com.erp.module.sales.controller.vo.ReportVOs.OpenOrderRow;
import com.erp.module.sales.controller.vo.ReportVOs.OrderTrace;
import com.erp.module.sales.controller.vo.ReportVOs.Performance;
import com.erp.module.sales.controller.vo.ReportVOs.PerformanceRow;
import com.erp.module.sales.controller.vo.ReportVOs.PeriodQuery;
import com.erp.module.sales.controller.vo.ReportVOs.QuoteSuccess;
import com.erp.module.sales.service.SalExportSupport;
import com.erp.module.sales.service.order.OrderService;
import com.erp.module.sales.service.report.SalesReportService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** 销售报表（需求 04-08） */
@Tag(name = "销售 - 报表")
@RestController
@RequestMapping("/api/sales/reports")
public class SalesReportController {

    static final List<ExcelColumn<OpenOrderRow>> OPEN_COLUMNS = List.of(
            ExcelColumn.text("orderNo", "订单号", OpenOrderRow::orderNo),
            ExcelColumn.text("customerName", "客户", OpenOrderRow::customerName),
            ExcelColumn.text("materialCode", "物料编码", OpenOrderRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", OpenOrderRow::materialName),
            ExcelColumn.number("orderQty", "订单数量", OpenOrderRow::orderQty),
            ExcelColumn.number("shippedQty", "已出货", OpenOrderRow::shippedQty),
            ExcelColumn.number("openQty", "未出货", OpenOrderRow::openQty),
            ExcelColumn.number("availableQty", "可用库存", OpenOrderRow::availableQty),
            ExcelColumn.date("requiredDate", "要求交期", OpenOrderRow::requiredDate),
            ExcelColumn.date("promisedDate", "承诺交期", OpenOrderRow::promisedDate),
            ExcelColumn.number("daysToDue", "距交期天数", OpenOrderRow::daysToDue),
            ExcelColumn.text("ownerName", "业务员", OpenOrderRow::ownerName));

    static final List<ExcelColumn<PerformanceRow>> PERF_COLUMNS = List.of(
            ExcelColumn.text("ownerName", "业务员", PerformanceRow::ownerName),
            ExcelColumn.text("deptName", "部门", PerformanceRow::deptName),
            ExcelColumn.number("orderAmount", "接单额", PerformanceRow::orderAmount),
            ExcelColumn.number("shipAmount", "出货额", PerformanceRow::shipAmount),
            ExcelColumn.number("receiptAmount", "回款额", PerformanceRow::receiptAmount),
            ExcelColumn.number("newCustomers", "新客户数", PerformanceRow::newCustomers),
            ExcelColumn.number("orderCount", "订单数", PerformanceRow::orderCount));

    static final List<ExcelColumn<CustomerRankRow>> RANK_COLUMNS = List.of(
            ExcelColumn.number("rank", "排名", CustomerRankRow::rank),
            ExcelColumn.text("customerCode", "客户编码", CustomerRankRow::customerCode),
            ExcelColumn.text("customerName", "客户", CustomerRankRow::customerName),
            ExcelColumn.number("orderAmount", "接单额", CustomerRankRow::orderAmount),
            ExcelColumn.number("shipAmount", "出货额", CustomerRankRow::shipAmount),
            ExcelColumn.number("share", "占比", CustomerRankRow::share),
            ExcelColumn.number("growth", "同比增长", CustomerRankRow::growth),
            ExcelColumn.text("abcClass", "ABC", CustomerRankRow::abcClass));

    private final SalesReportService service;
    private final OrderService orderService;
    private final SalExportSupport exportSupport;

    public SalesReportController(SalesReportService service, OrderService orderService, SalExportSupport exportSupport) {
        this.service = service;
        this.orderService = orderService;
        this.exportSupport = exportSupport;
    }

    /** 订单明细（2.1） */
    @GetMapping("/order-lines")
    @PreAuthorize("@ss.has('sales:report:query')")
    public CommonResult<PageResult<OrderLineRow>> orderLines(@Valid OrderQuery q) {
        List<OrderLineRow> all = orderService.lineRows(q, 20000);
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), all.size());
        return CommonResult.success(new PageResult<>(all.subList(from, Math.min(from + q.getPageSize(), all.size())), all.size()));
    }

    @GetMapping("/order-lines/export")
    @PreAuthorize("@ss.has('sales:report:export')")
    public void exportOrderLines(@Valid OrderQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "订单明细", SalesOrderController.LINE_EXPORT_COLUMNS, q.getColumns(), limit -> orderService.lineRows(q, limit));
    }

    /** 未交订单（2.2） */
    @GetMapping("/open-orders")
    @PreAuthorize("@ss.has('sales:report:query')")
    public CommonResult<PageResult<OpenOrderRow>> openOrders(@Valid OpenOrderQuery q) {
        return CommonResult.success(service.openOrders(q));
    }

    @GetMapping("/open-orders/export")
    @PreAuthorize("@ss.has('sales:report:export')")
    public void exportOpenOrders(@Valid OpenOrderQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "未交订单", OPEN_COLUMNS, q.getColumns(), limit -> service.openOrderRows(q).stream().limit(limit).toList());
    }

    /** 订单执行跟踪（2.3） */
    @GetMapping("/order-trace/{orderLineId}")
    @PreAuthorize("@ss.hasAny('sales:report:query', 'sales:order:query')")
    public CommonResult<OrderTrace> trace(@PathVariable Long orderLineId) {
        return CommonResult.success(service.trace(orderLineId));
    }

    /** 业务员业绩（2.4） */
    @GetMapping("/performance")
    @PreAuthorize("@ss.has('sales:report:query')")
    public CommonResult<Performance> performance(PeriodQuery q) {
        return CommonResult.success(service.performance(q));
    }

    @GetMapping("/performance/export")
    @PreAuthorize("@ss.has('sales:report:export')")
    public void exportPerformance(PeriodQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "业务员业绩", PERF_COLUMNS, null, limit -> service.performance(q).rows());
    }

    /** 报价成功率（2.5） */
    @GetMapping("/quotation-success")
    @PreAuthorize("@ss.has('sales:report:query')")
    public CommonResult<QuoteSuccess> quoteSuccess(PeriodQuery q) {
        return CommonResult.success(service.quoteSuccess(q));
    }

    /** 客户销售排名（2.6） */
    @GetMapping("/customer-ranking")
    @PreAuthorize("@ss.has('sales:report:query')")
    public CommonResult<List<CustomerRankRow>> customerRanking(PeriodQuery q) {
        return CommonResult.success(service.customerRanking(q));
    }

    @GetMapping("/customer-ranking/export")
    @PreAuthorize("@ss.has('sales:report:export')")
    public void exportRanking(PeriodQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "客户销售排名", RANK_COLUMNS, null, limit -> service.customerRanking(q));
    }
}
