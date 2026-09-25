package com.erp.module.purchase.api.order;

import com.erp.common.event.DomainEvent;

import java.time.LocalDate;
import java.util.List;

/** 供应商回复交期、跟催更新承诺日期后发布（同一事务内），PMC 据此重新评估缺料。 */
public class PurchaseDeliveryDateChangedEvent extends DomainEvent {

    /** @param delayed 确认交期晚于要求日期 */
    public record Line(Long orderLineId, Long materialId, LocalDate requiredDate, LocalDate confirmedDate, boolean delayed) {
    }

    private final Long orderId;
    private final String orderNo;
    private final List<Line> lines;

    public PurchaseDeliveryDateChangedEvent(Long orderId, String orderNo, List<Line> lines) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.lines = List.copyOf(lines);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public List<Line> getLines() { return lines; }
}
