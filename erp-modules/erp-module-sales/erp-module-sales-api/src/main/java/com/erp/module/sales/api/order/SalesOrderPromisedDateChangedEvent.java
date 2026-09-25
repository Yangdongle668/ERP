package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

import java.time.LocalDate;

/** 承诺交期写入（PMC 交期回复后），delayed 为承诺交期晚于要求交期 */
public class SalesOrderPromisedDateChangedEvent extends DomainEvent {

    private final Long orderId;
    private final Long lineId;
    private final LocalDate requiredDate;
    private final LocalDate promisedDate;
    private final boolean delayed;

    public SalesOrderPromisedDateChangedEvent(Long orderId, Long lineId, LocalDate requiredDate, LocalDate promisedDate, boolean delayed) {
        this.orderId = orderId;
        this.lineId = lineId;
        this.requiredDate = requiredDate;
        this.promisedDate = promisedDate;
        this.delayed = delayed;
    }

    public Long getOrderId() { return orderId; }
    public Long getLineId() { return lineId; }
    public LocalDate getRequiredDate() { return requiredDate; }
    public LocalDate getPromisedDate() { return promisedDate; }
    public boolean isDelayed() { return delayed; }
}
