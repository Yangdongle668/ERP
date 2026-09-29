package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.ReportVOs.AgingDoc;
import com.erp.module.finance.controller.vo.ReportVOs.AgingReport;
import com.erp.module.finance.controller.vo.ReportVOs.AgingRow;
import com.erp.module.finance.controller.vo.ReportVOs.Statement;
import com.erp.module.finance.controller.vo.ReportVOs.StatementLine;
import com.erp.module.finance.service.FinExportSupport;
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

    private final FinReportService service;
    private final FinExportSupport exportSupport;

    public FinReportController(FinReportService service, FinExportSupport exportSupport) {
        this.service = service;
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
}
