package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.AnalysisVOs.AccountBalance;
import com.erp.module.finance.controller.vo.AnalysisVOs.AccountBalanceRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.CashDaily;
import com.erp.module.finance.controller.vo.AnalysisVOs.CashDailyRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.Ledger;
import com.erp.module.finance.controller.vo.AnalysisVOs.LedgerLine;
import com.erp.module.finance.controller.vo.AnalysisVOs.MarginReport;
import com.erp.module.finance.controller.vo.AnalysisVOs.MarginRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.PlItem;
import com.erp.module.finance.controller.vo.AnalysisVOs.ProfitLoss;
import com.erp.module.finance.controller.vo.ReportVOs.AgingDoc;
import com.erp.module.finance.controller.vo.ReportVOs.AgingReport;
import com.erp.module.finance.controller.vo.ReportVOs.AgingRow;
import com.erp.module.finance.controller.vo.ReportVOs.Statement;
import com.erp.module.finance.controller.vo.ReportVOs.StatementLine;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.report.FinAnalysisService;
import com.erp.module.finance.service.report.FinReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "财务 - 报表")
@RestController
@RequestMapping("/api/finance/reports")
public class FinReportController {

    static List<ExcelColumn<AgingRow>> agingColumns(String partner) {
        return List.of(
                ExcelColumn.text("partnerCode", partner + "编码", AgingRow::partnerCode),
                ExcelColumn.text("partnerName", partner, AgingRow::partnerName),
                ExcelColumn.text("currency", "币别", AgingRow::currency),
                ExcelColumn.number("total", "未核销余额", AgingRow::total),
                ExcelColumn.number("notDue", "未到期", AgingRow::notDue),
                ExcelColumn.number("d1to30", "逾期 1～30 天", AgingRow::d1to30),
                ExcelColumn.number("d31to60", "31～60 天", AgingRow::d31to60),
                ExcelColumn.number("d61to90", "61～90 天", AgingRow::d61to90),
                ExcelColumn.number("d91to180", "91～180 天", AgingRow::d91to180),
                ExcelColumn.number("over180", "> 180 天", AgingRow::over180),
                ExcelColumn.number("rate", "折算汇率", AgingRow::rate),
                ExcelColumn.number("totalBase", "本位币", AgingRow::totalBase));
    }

    static final List<ExcelColumn<StatementLine>> STATEMENT_COLUMNS = List.of(
            ExcelColumn.date("date", "日期", StatementLine::date),
            ExcelColumn.text("docNo", "单号", StatementLine::docNo),
            ExcelColumn.text("summary", "摘要", StatementLine::summary),
            ExcelColumn.number("debit", "增加", StatementLine::debit),
            ExcelColumn.number("credit", "减少", StatementLine::credit),
            ExcelColumn.number("balance", "余额", StatementLine::balance));

    static final List<ExcelColumn<CashDailyRow>> CASH_COLUMNS = List.of(
            ExcelColumn.date("date", "日期", CashDailyRow::date),
            ExcelColumn.text("bankCode", "账户编码", CashDailyRow::bankCode),
            ExcelColumn.text("bankName", "账户", CashDailyRow::bankName),
            ExcelColumn.text("currency", "币别", CashDailyRow::currency),
            ExcelColumn.number("opening", "期初余额", CashDailyRow::opening),
            ExcelColumn.number("income", "收款", CashDailyRow::income),
            ExcelColumn.number("expense", "付款", CashDailyRow::expense),
            ExcelColumn.number("closing", "期末余额", CashDailyRow::closing));

    static final List<ExcelColumn<MarginRow>> MARGIN_COLUMNS = List.of(
            ExcelColumn.number("rank", "排名", r -> java.math.BigDecimal.valueOf(r.rank())),
            ExcelColumn.text("orderNo", "订单号", MarginRow::orderNo),
            ExcelColumn.text("customerName", "客户", MarginRow::customerName),
            ExcelColumn.text("salesmanName", "业务员", MarginRow::salesmanName),
            ExcelColumn.text("materialCode", "产品编码", MarginRow::materialCode),
            ExcelColumn.text("materialName", "产品名称", MarginRow::materialName),
            ExcelColumn.number("qty", "数量", MarginRow::qty),
            ExcelColumn.number("revenue", "收入", MarginRow::revenue),
            ExcelColumn.text("cost", "成本", r -> r.cost() == null ? "未计算" : r.cost().toPlainString()),
            ExcelColumn.number("margin", "毛利", MarginRow::margin),
            ExcelColumn.number("marginRate", "毛利率（%）", MarginRow::marginRate));

    static final List<ExcelColumn<PlItem>> PL_COLUMNS = List.of(
            ExcelColumn.text("label", "项目", PlItem::label),
            ExcelColumn.number("month", "本月", PlItem::month),
            ExcelColumn.number("ytd", "本年累计", PlItem::ytd),
            ExcelColumn.number("lastYear", "上年同期", PlItem::lastYear));

    static final List<ExcelColumn<AccountBalanceRow>> BALANCE_COLUMNS = List.of(
            ExcelColumn.text("accountCode", "科目编码", AccountBalanceRow::accountCode),
            ExcelColumn.text("accountName", "科目名称", AccountBalanceRow::accountName),
            ExcelColumn.text("direction", "方向", r -> "CREDIT".equals(r.direction()) ? "贷" : "借"),
            ExcelColumn.number("opening", "期初余额", AccountBalanceRow::opening),
            ExcelColumn.number("debit", "借方发生", AccountBalanceRow::debit),
            ExcelColumn.number("credit", "贷方发生", AccountBalanceRow::credit),
            ExcelColumn.number("closing", "期末余额", AccountBalanceRow::closing));

    static final List<ExcelColumn<LedgerLine>> LEDGER_COLUMNS = List.of(
            ExcelColumn.date("date", "日期", LedgerLine::date),
            ExcelColumn.text("voucherNo", "凭证号", LedgerLine::voucherNo),
            ExcelColumn.text("summary", "摘要", LedgerLine::summary),
            ExcelColumn.text("accountCode", "科目", LedgerLine::accountCode),
            ExcelColumn.number("debit", "借方", LedgerLine::debit),
            ExcelColumn.number("credit", "贷方", LedgerLine::credit),
            ExcelColumn.number("balance", "余额", LedgerLine::balance));

    private final FinReportService service;
    private final FinAnalysisService analysis;
    private final FinExportSupport exportSupport;

    public FinReportController(FinReportService service, FinAnalysisService analysis, FinExportSupport exportSupport) {
        this.service = service;
        this.analysis = analysis;
        this.exportSupport = exportSupport;
    }

    // ==================== 账龄 ====================

    @GetMapping("/ar-aging")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<AgingReport> arAging(@RequestParam(required = false) LocalDate asOf, @RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) String currency) {
        return CommonResult.success(service.arAging(asOf, customerId, currency));
    }

    @GetMapping("/ar-aging/docs")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<List<AgingDoc>> arAgingDocs(@RequestParam(required = false) LocalDate asOf, @RequestParam Long customerId,
                                                    @RequestParam(required = false) String currency) {
        return CommonResult.success(service.arAgingDocs(asOf, customerId, currency));
    }

    @GetMapping("/ar-aging/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void arAgingExport(@RequestParam(required = false) LocalDate asOf, @RequestParam(required = false) Long customerId,
                              @RequestParam(required = false) String currency, @RequestParam(required = false) String columns,
                              HttpServletResponse response) throws IOException {
        exportSupport.export(response, "应收账龄表", agingColumns("客户"), columns,
                limit -> service.arAging(asOf, customerId, currency).rows().stream().limit(limit).toList());
    }

    @GetMapping("/ap-aging")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<AgingReport> apAging(@RequestParam(required = false) LocalDate asOf, @RequestParam(required = false) Long supplierId,
                                             @RequestParam(required = false) String currency) {
        return CommonResult.success(service.apAging(asOf, supplierId, currency));
    }

    @GetMapping("/ap-aging/docs")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<List<AgingDoc>> apAgingDocs(@RequestParam(required = false) LocalDate asOf, @RequestParam Long supplierId,
                                                    @RequestParam(required = false) String currency) {
        return CommonResult.success(service.apAgingDocs(asOf, supplierId, currency));
    }

    @GetMapping("/ap-aging/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void apAgingExport(@RequestParam(required = false) LocalDate asOf, @RequestParam(required = false) Long supplierId,
                              @RequestParam(required = false) String currency, @RequestParam(required = false) String columns,
                              HttpServletResponse response) throws IOException {
        exportSupport.export(response, "应付账龄表", agingColumns("供应商"), columns,
                limit -> service.apAging(asOf, supplierId, currency).rows().stream().limit(limit).toList());
    }

    // ==================== 对账单 ====================

    @GetMapping("/customer-statement")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<Statement> customerStatement(@RequestParam Long customerId, @RequestParam(required = false) String currency,
                                                     @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        return CommonResult.success(service.customerStatement(customerId, currency, dateFrom, dateTo));
    }

    @GetMapping("/customer-statement/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void customerStatementExport(@RequestParam Long customerId, @RequestParam(required = false) String currency,
                                        @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                        @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        Statement s = service.customerStatement(customerId, currency, dateFrom, dateTo);
        exportSupport.export(response, "客户对账单-" + s.partnerName(), STATEMENT_COLUMNS, columns, limit -> s.lines().stream().limit(limit).toList());
    }

    @GetMapping("/customer-statement/print-data")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<Map<String, Object>> customerStatementPrint(@RequestParam Long customerId, @RequestParam(required = false) String currency,
                                                                    @RequestParam(required = false) LocalDate dateFrom,
                                                                    @RequestParam(required = false) LocalDate dateTo) {
        return CommonResult.success(service.customerStatementPrint(customerId, currency, dateFrom, dateTo));
    }

    @GetMapping("/supplier-statement")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<Statement> supplierStatement(@RequestParam Long supplierId, @RequestParam(required = false) String currency,
                                                     @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        return CommonResult.success(service.supplierStatement(supplierId, currency, dateFrom, dateTo));
    }

    @GetMapping("/supplier-statement/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void supplierStatementExport(@RequestParam Long supplierId, @RequestParam(required = false) String currency,
                                        @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                        @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        Statement s = service.supplierStatement(supplierId, currency, dateFrom, dateTo);
        exportSupport.export(response, "供应商对账单-" + s.partnerName(), STATEMENT_COLUMNS, columns, limit -> s.lines().stream().limit(limit).toList());
    }
    // ==================== 收付款日报 ====================

    @GetMapping("/cash-daily")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<CashDaily> cashDaily(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                             @RequestParam(required = false) Long bankAccountId) {
        return CommonResult.success(analysis.cashDaily(dateFrom, dateTo, bankAccountId));
    }

    @GetMapping("/cash-daily/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void cashDailyExport(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                @RequestParam(required = false) Long bankAccountId, @RequestParam(required = false) String columns,
                                HttpServletResponse response) throws IOException {
        exportSupport.export(response, "收付款日报", CASH_COLUMNS, columns,
                limit -> analysis.cashDaily(dateFrom, dateTo, bankAccountId).rows().stream().limit(limit).toList());
    }

    // ==================== 毛利 ====================

    @GetMapping("/order-margin")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<MarginReport> orderMargin(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                                  @RequestParam(required = false) String group, @RequestParam(required = false) Long customerId) {
        return CommonResult.success(analysis.margin(dateFrom, dateTo, group == null ? "LINE" : group, customerId, null));
    }

    @GetMapping("/order-margin/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void orderMarginExport(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                  @RequestParam(required = false) String group, @RequestParam(required = false) Long customerId,
                                  @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "订单毛利表", MARGIN_COLUMNS, columns,
                limit -> analysis.margin(dateFrom, dateTo, group == null ? "LINE" : group, customerId, null).rows().stream().limit(limit).toList());
    }

    @GetMapping("/product-margin")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<MarginReport> productMargin(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                                    @RequestParam(required = false) Long materialId) {
        return CommonResult.success(analysis.margin(dateFrom, dateTo, "PRODUCT", null, materialId));
    }

    @GetMapping("/product-margin/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void productMarginExport(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                    @RequestParam(required = false) Long materialId, @RequestParam(required = false) String columns,
                                    HttpServletResponse response) throws IOException {
        exportSupport.export(response, "产品毛利排名", MARGIN_COLUMNS, columns,
                limit -> analysis.margin(dateFrom, dateTo, "PRODUCT", null, materialId).rows().stream().limit(limit).toList());
    }

    @GetMapping("/customer-margin")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<MarginReport> customerMargin(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                                     @RequestParam(required = false) Long customerId) {
        return CommonResult.success(analysis.margin(dateFrom, dateTo, "CUSTOMER", customerId, null));
    }

    @GetMapping("/customer-margin/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void customerMarginExport(@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
                                     @RequestParam(required = false) Long customerId, @RequestParam(required = false) String columns,
                                     HttpServletResponse response) throws IOException {
        exportSupport.export(response, "客户毛利排名", MARGIN_COLUMNS, columns,
                limit -> analysis.margin(dateFrom, dateTo, "CUSTOMER", customerId, null).rows().stream().limit(limit).toList());
    }

    // ==================== 损益、科目余额、明细账 ====================

    @GetMapping("/profit-loss")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<ProfitLoss> profitLoss(@RequestParam(required = false) String period) {
        return CommonResult.success(analysis.profitLoss(period));
    }

    @GetMapping("/profit-loss/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void profitLossExport(@RequestParam(required = false) String period, @RequestParam(required = false) String columns,
                                 HttpServletResponse response) throws IOException {
        exportSupport.export(response, "月度损益简表", PL_COLUMNS, columns, limit -> analysis.profitLoss(period).items().stream().limit(limit).toList());
    }

    @GetMapping("/account-balance")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<AccountBalance> accountBalance(@RequestParam(required = false) String periodFrom, @RequestParam(required = false) String periodTo,
                                                       @RequestParam(defaultValue = "false") boolean includeUnposted,
                                                       @RequestParam(required = false) Integer maxLevel) {
        return CommonResult.success(analysis.accountBalance(periodFrom, periodTo, includeUnposted, maxLevel));
    }

    @GetMapping("/account-balance/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void accountBalanceExport(@RequestParam(required = false) String periodFrom, @RequestParam(required = false) String periodTo,
                                     @RequestParam(defaultValue = "false") boolean includeUnposted, @RequestParam(required = false) Integer maxLevel,
                                     @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "科目余额表", BALANCE_COLUMNS, columns,
                limit -> analysis.accountBalance(periodFrom, periodTo, includeUnposted, maxLevel).rows().stream().limit(limit).toList());
    }

    @GetMapping("/ledger")
    @PreAuthorize("@ss.has('fin:report:query')")
    public CommonResult<Ledger> ledger(@RequestParam String accountCode, @RequestParam(required = false) String periodFrom,
                                       @RequestParam(required = false) String periodTo, @RequestParam(defaultValue = "false") boolean includeUnposted) {
        return CommonResult.success(analysis.ledger(accountCode, periodFrom, periodTo, includeUnposted));
    }

    @GetMapping("/ledger/export")
    @PreAuthorize("@ss.has('fin:report:export')")
    public void ledgerExport(@RequestParam String accountCode, @RequestParam(required = false) String periodFrom,
                             @RequestParam(required = false) String periodTo, @RequestParam(defaultValue = "false") boolean includeUnposted,
                             @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "明细账-" + accountCode, LEDGER_COLUMNS, columns,
                limit -> analysis.ledger(accountCode, periodFrom, periodTo, includeUnposted).lines().stream().limit(limit).toList());
    }
}
