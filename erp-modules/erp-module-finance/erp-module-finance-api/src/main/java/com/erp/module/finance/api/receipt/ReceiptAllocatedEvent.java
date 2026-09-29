package com.erp.module.finance.api.receipt;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 收款分配到销售订单（预收确认、核销到应收）：销售回款计划。amount 为负表示冲回 */
public class ReceiptAllocatedEvent extends DomainEvent {

    private final Long receiptId;
    private final String receiptNo;
    private final Long customerId;
    private final Long orderId;
    private final String currency;
    private final BigDecimal amount;
    private final LocalDate receiptDate;

    public ReceiptAllocatedEvent(Long receiptId, String receiptNo, Long customerId, Long orderId, String currency, BigDecimal amount, LocalDate receiptDate) {
        this.receiptId = receiptId;
        this.receiptNo = receiptNo;
        this.customerId = customerId;
        this.orderId = orderId;
        this.currency = currency;
        this.amount = amount;
        this.receiptDate = receiptDate;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }
}
