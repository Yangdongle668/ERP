package com.erp.module.finance.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 财务分析报表（12-08 P1）：收付款日报、毛利、损益、科目余额、明细账 */
public final class AnalysisVOs {

    private AnalysisVOs() {
    }

    /** 收付款日报：按日期、账户（原币） */
    public record CashDailyRow(LocalDate date, Long bankAccountId, String bankCode, String bankName, String currency, BigDecimal opening, BigDecimal income,
                               BigDecimal expense, BigDecimal closing, int receiptCount, int paymentCount) {
    }

    public record CashDaily(LocalDate dateFrom, LocalDate dateTo, List<CashDailyRow> rows) {
    }

    /**
     * 毛利行：group = LINE（订单行）/ ORDER / CUSTOMER / SALESMAN / PRODUCT；cost 为空表示成本未计算
     */
    public record MarginRow(String key, Long orderId, String orderNo, Long customerId, String customerName, Long salesmanId, String salesmanName,
                            Long materialId, String materialCode, String materialName, BigDecimal qty, BigDecimal revenue, BigDecimal cost,
                            BigDecimal margin, BigDecimal marginRate, int rank) {
    }

    /** uncalculatedPeriods：成本未计算的期间（成本显示“未计算”） */
    public record MarginReport(LocalDate dateFrom, LocalDate dateTo, String group, List<MarginRow> rows, BigDecimal totalRevenue, BigDecimal totalCost,
                               BigDecimal totalMargin, BigDecimal marginRate, List<String> uncalculatedPeriods) {
    }

    /** 损益项：金额为空表示成本未计算 */
    public record PlItem(String key, String label, BigDecimal month, BigDecimal ytd, BigDecimal lastYear, boolean bold) {
    }

    public record ProfitLoss(String period, List<PlItem> items, List<String> uncalculatedPeriods) {
    }

    /** 科目余额：余额以科目方向为正 */
    public record AccountBalanceRow(String accountCode, String accountName, int level, boolean leaf, String direction, BigDecimal opening,
                                    BigDecimal debit, BigDecimal credit, BigDecimal closing) {
    }

    public record AccountBalance(String periodFrom, String periodTo, boolean includeUnposted, List<AccountBalanceRow> rows, BigDecimal totalDebit,
                                 BigDecimal totalCredit) {
    }

    public record LedgerLine(LocalDate date, String period, Long voucherId, String voucherNo, String summary, String accountCode, BigDecimal debit,
                             BigDecimal credit, String direction, BigDecimal balance) {
    }

    public record Ledger(String accountCode, String accountName, String direction, String periodFrom, String periodTo, BigDecimal opening,
                         BigDecimal debit, BigDecimal credit, BigDecimal closing, List<LedgerLine> lines) {
    }
}
