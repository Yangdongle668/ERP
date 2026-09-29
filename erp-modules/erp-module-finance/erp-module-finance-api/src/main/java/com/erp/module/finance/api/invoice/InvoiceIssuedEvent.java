package com.erp.module.finance.api.invoice;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 销项发票登记 / 作废：销售订单行已开票数量。作废时 lines 数量为负 */
public class InvoiceIssuedEvent extends DomainEvent {

    public record Line(Long orderLineId, BigDecimal qty, BigDecimal amount) {
    }

    private final Long invoiceId;
    private final String invoiceNo;
    private final Long customerId;
    private final LocalDate invoiceDate;
    private final List<Line> lines;

    public InvoiceIssuedEvent(Long invoiceId, String invoiceNo, Long customerId, LocalDate invoiceDate, List<Line> lines) {
        this.invoiceId = invoiceId;
        this.invoiceNo = invoiceNo;
        this.customerId = customerId;
        this.invoiceDate = invoiceDate;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public List<Line> getLines() {
        return lines;
    }
}
