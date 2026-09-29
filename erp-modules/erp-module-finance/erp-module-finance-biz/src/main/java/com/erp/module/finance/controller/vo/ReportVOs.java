package com.erp.module.finance.controller.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 财务报表（12-08） */
public final class ReportVOs {

    private ReportVOs() {
    }

    /** 账龄（按客户 / 供应商 + 币别）；本位币按截止日汇率折算（FIN-RPT-R02） */
    public record AgingRow(Long partnerId, String partnerCode, String partnerName, String currency, BigDecimal rate, BigDecimal total,
                           BigDecimal notDue, BigDecimal d1to30, BigDecimal d31to60, BigDecimal d61to90, BigDecimal d91to180, BigDecimal over180,
                           BigDecimal totalBase, BigDecimal overdueBase) {
    }

    public record AgingReport(LocalDate asOf, String baseCurrency, List<AgingRow> rows, BigDecimal totalBase, BigDecimal overdueBase) {
    }

    /** 账龄下钻：单据 */
    public record AgingDoc(Long id, String docNo, String docType, String sourceNo, LocalDate bizDate, LocalDate dueDate, int overdueDays, String bucket,
                           String currency, BigDecimal totalAmount, BigDecimal openAmount) {
    }

    public record StatementLine(LocalDate date, String docType, Long docId, String docNo, String summary, String currency, BigDecimal debit,
                                BigDecimal credit, BigDecimal balance) {
    }

    /**
     * 往来对账单：期末 = 期初 + 本期应收（应付）− 本期收款（付款）；ledgerBalance 为当前单据余额（应收未核销 − 收款未核销），
     * 截止日不早于今天且两者不一致时 mismatch = true（FIN-RPT-R03）
     */
    public record Statement(Long partnerId, String partnerCode, String partnerName, String partnerNameEn, String currency, LocalDate dateFrom,
                            LocalDate dateTo, BigDecimal opening, BigDecimal debit, BigDecimal credit, BigDecimal verified, BigDecimal closing,
                            BigDecimal ledgerBalance, boolean mismatch, List<StatementLine> lines) {
    }
}
