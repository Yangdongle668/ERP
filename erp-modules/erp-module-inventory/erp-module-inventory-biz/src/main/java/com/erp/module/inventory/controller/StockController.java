package com.erp.module.inventory.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.inventory.controller.vo.ReportVOs.AgingQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.AgingResult;
import com.erp.module.inventory.controller.vo.ReportVOs.AlertCounts;
import com.erp.module.inventory.controller.vo.ReportVOs.AlertRow;
import com.erp.module.inventory.controller.vo.ReportVOs.SlowQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.SlowRow;
import com.erp.module.inventory.controller.vo.ReportVOs.StockPage;
import com.erp.module.inventory.controller.vo.ReportVOs.StockQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.StockRow;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryResult;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryRow;
import com.erp.module.inventory.controller.vo.ReportVOs.TxnQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.TxnRow;
import com.erp.module.inventory.service.InventoryExportSupport;
import com.erp.module.inventory.service.StockReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** 库存查询与报表（需求 08-08 第 4 节） */
@Tag(name = "仓库 - 库存查询与报表")
@RestController("invStockController")
@RequestMapping("/api/inventory")
public class StockController {

    static final List<ExcelColumn<StockRow>> STOCK_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", StockRow::materialCode),
            ExcelColumn.text("materialName", "名称", StockRow::materialName),
            ExcelColumn.text("materialSpec", "规格", StockRow::materialSpec),
            ExcelColumn.text("baseUom", "单位", StockRow::baseUom),
            ExcelColumn.text("categoryName", "类别", StockRow::categoryName),
            ExcelColumn.text("warehouseName", "仓库", StockRow::warehouseName),
            ExcelColumn.text("locationCode", "库位", StockRow::locationCode),
            ExcelColumn.text("batchNo", "批次", StockRow::batchNo),
            ExcelColumn.number("onHandQty", "现存量", StockRow::onHandQty),
            ExcelColumn.number("availableQty", "可用量", StockRow::availableQty),
            ExcelColumn.number("refCost", "参考单价", StockRow::refCost),
            ExcelColumn.number("amount", "金额", StockRow::amount),
            ExcelColumn.date("lastInDate", "最近入库", StockRow::lastInDate),
            ExcelColumn.date("lastOutDate", "最近出库", StockRow::lastOutDate));

    static final List<ExcelColumn<TxnRow>> TXN_COLUMNS = List.of(
            ExcelColumn.date("bizDate", "业务日期", TxnRow::bizDate),
            ExcelColumn.text("docNo", "单号", TxnRow::docNo),
            ExcelColumn.text("bizType", "类型", TxnRow::bizType),
            ExcelColumn.text("sourceNo", "来源单号", TxnRow::sourceNo),
            ExcelColumn.text("materialCode", "物料编码", TxnRow::materialCode),
            ExcelColumn.text("materialName", "名称", TxnRow::materialName),
            ExcelColumn.text("warehouseName", "仓库", TxnRow::warehouseName),
            ExcelColumn.text("locationCode", "库位", TxnRow::locationCode),
            ExcelColumn.text("batchNo", "批次", TxnRow::batchNo),
            ExcelColumn.number("inQty", "入库数量", TxnRow::inQty),
            ExcelColumn.number("outQty", "出库数量", TxnRow::outQty),
            ExcelColumn.number("balanceQty", "结存数量", TxnRow::balanceQty),
            ExcelColumn.number("unitCost", "单价", TxnRow::unitCost),
            ExcelColumn.number("amount", "金额", TxnRow::amount),
            ExcelColumn.text("operatorName", "操作人", TxnRow::operatorName));

    static final List<ExcelColumn<SummaryRow>> SUMMARY_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", SummaryRow::materialCode),
            ExcelColumn.text("materialName", "名称", SummaryRow::materialName),
            ExcelColumn.text("materialSpec", "规格", SummaryRow::materialSpec),
            ExcelColumn.text("baseUom", "单位", SummaryRow::baseUom),
            ExcelColumn.text("warehouseName", "仓库", SummaryRow::warehouseName),
            ExcelColumn.number("openingQty", "期初数量", SummaryRow::openingQty),
            ExcelColumn.number("openingAmount", "期初金额", SummaryRow::openingAmount),
            ExcelColumn.number("inQty", "本期入库数量", SummaryRow::inQty),
            ExcelColumn.number("inAmount", "本期入库金额", SummaryRow::inAmount),
            ExcelColumn.number("outQty", "本期出库数量", SummaryRow::outQty),
            ExcelColumn.number("outAmount", "本期出库金额", SummaryRow::outAmount),
            ExcelColumn.number("closingQty", "期末数量", SummaryRow::closingQty),
            ExcelColumn.number("closingAmount", "期末金额", SummaryRow::closingAmount));

    static final List<ExcelColumn<SlowRow>> SLOW_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", SlowRow::materialCode),
            ExcelColumn.text("materialName", "名称", SlowRow::materialName),
            ExcelColumn.text("materialSpec", "规格", SlowRow::materialSpec),
            ExcelColumn.number("qty", "现存量", SlowRow::qty),
            ExcelColumn.number("amount", "金额", SlowRow::amount),
            ExcelColumn.date("lastInDate", "最近入库", SlowRow::lastInDate),
            ExcelColumn.date("lastOutDate", "最近出库", SlowRow::lastOutDate),
            ExcelColumn.number("idleDays", "无出库天数", SlowRow::idleDays),
            ExcelColumn.text("bomUsed", "BOM 引用", r -> r.bomUsed() ? "是" : "否"));

    static final List<ExcelColumn<AlertRow>> ALERT_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", AlertRow::materialCode),
            ExcelColumn.text("materialName", "名称", AlertRow::materialName),
            ExcelColumn.text("warehouseName", "仓库", AlertRow::warehouseName),
            ExcelColumn.text("batchNo", "批次", AlertRow::batchNo),
            ExcelColumn.number("qty", "数量", AlertRow::qty),
            ExcelColumn.number("safetyStock", "安全库存", AlertRow::safetyStock),
            ExcelColumn.number("maxStock", "最高库存", AlertRow::maxStock),
            ExcelColumn.number("availableQty", "可用量", AlertRow::availableQty),
            ExcelColumn.number("gap", "缺口/超出", AlertRow::gap),
            ExcelColumn.date("expireDate", "到期日", AlertRow::expireDate),
            ExcelColumn.number("waitHours", "已等待小时", AlertRow::waitHours),
            ExcelColumn.text("buyerName", "采购员", AlertRow::buyerName));

    private final StockReportService service;
    private final InventoryExportSupport exportSupport;

    public StockController(StockReportService service, InventoryExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @Operation(summary = "即时库存：groupBy = MATERIAL / WAREHOUSE / BATCH")
    @GetMapping("/stocks")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<StockPage> stocks(@Valid StockQuery q) {
        return CommonResult.success(service.stocks(q));
    }

    @GetMapping("/stocks/export")
    @PreAuthorize("@ss.has('inv:stock:export')")
    public void exportStocks(@Valid StockQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "库存查询", STOCK_COLUMNS, columns, limit -> service.stocksForExport(q, limit));
    }

    @Operation(summary = "库存流水：业务日期跨度不超过 1 年")
    @GetMapping("/stock-txns")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<PageResult<TxnRow>> txns(@Valid TxnQuery q) {
        return CommonResult.success(service.txns(q));
    }

    @GetMapping("/stock-txns/export")
    @PreAuthorize("@ss.has('inv:stock:export')")
    public void exportTxns(@Valid TxnQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "库存流水", TXN_COLUMNS, columns, limit -> service.txnsForExport(q, limit));
    }

    @GetMapping("/reports/in-out-summary")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<SummaryResult> inOutSummary(SummaryQuery q) {
        return CommonResult.success(service.inOutSummary(q));
    }

    @GetMapping("/reports/in-out-summary/export")
    @PreAuthorize("@ss.has('inv:stock:export')")
    public void exportSummary(SummaryQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "收发存汇总", SUMMARY_COLUMNS, columns, limit -> service.inOutSummary(q).rows());
    }

    @GetMapping("/reports/aging")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<AgingResult> aging(AgingQuery q) {
        return CommonResult.success(service.aging(q));
    }

    @GetMapping("/reports/slow-moving")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<List<SlowRow>> slowMoving(SlowQuery q) {
        return CommonResult.success(service.slowMoving(q));
    }

    @GetMapping("/reports/slow-moving/export")
    @PreAuthorize("@ss.has('inv:stock:export')")
    public void exportSlow(SlowQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "呆滞料", SLOW_COLUMNS, columns, limit -> service.slowMoving(q));
    }

    @Operation(summary = "库存预警：type = LOW / HIGH / EXPIRY / QC_OVERDUE")
    @GetMapping("/alerts")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<List<AlertRow>> alerts(@RequestParam(defaultValue = "LOW") String type) {
        return CommonResult.success(service.alerts(type));
    }

    @GetMapping("/alerts/counts")
    @PreAuthorize("@ss.has('inv:stock:query')")
    public CommonResult<AlertCounts> alertCounts() {
        return CommonResult.success(service.alertCounts());
    }

    @GetMapping("/alerts/export")
    @PreAuthorize("@ss.has('inv:stock:export')")
    public void exportAlerts(@RequestParam(defaultValue = "LOW") String type, @RequestParam(required = false) String columns,
                             HttpServletResponse response) throws IOException {
        exportSupport.export(response, "库存预警", ALERT_COLUMNS, columns, limit -> service.alerts(type));
    }
}
