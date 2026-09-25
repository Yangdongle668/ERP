package com.erp.module.purchase.api.statement;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 供应商确认对账单后发布（同一事务内），财务据此生成应付单。 */
public class PurchaseStatementConfirmedEvent extends DomainEvent {

    /**
     * @param lineType GOODS / RETURN / PROCESS_FEE / ADJUST；金额为原币，退货与扣款为负
     */
    public record Line(Long lineId, String lineType, String sourceType, Long sourceId, Long sourceLineId, String sourceNo, String orderNo,
                       Long materialId, LocalDate bizDate, BigDecimal qty, BigDecimal priceInclTax, BigDecimal taxRate,
                       BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    private final Long statementId;
    private final String statementNo;
    private final Long supplierId;
    private final String currency;
    private final LocalDate periodFrom;
    private final LocalDate periodTo;
    private final BigDecimal totalAmount;
    private final BigDecimal taxAmount;
    private final List<Line> lines;

    public PurchaseStatementConfirmedEvent(Long statementId, String statementNo, Long supplierId, String currency, LocalDate periodFrom,
                                           LocalDate periodTo, BigDecimal totalAmount, BigDecimal taxAmount, List<Line> lines) {
        this.statementId = statementId;
        this.statementNo = statementNo;
        this.supplierId = supplierId;
        this.currency = currency;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.totalAmount = totalAmount;
        this.taxAmount = taxAmount;
        this.lines = List.copyOf(lines);
    }

    public Long getStatementId() { return statementId; }
    public String getStatementNo() { return statementNo; }
    public Long getSupplierId() { return supplierId; }
    public String getCurrency() { return currency; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public List<Line> getLines() { return lines; }
}
