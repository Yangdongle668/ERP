package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 销售订单关闭（PMC 从需求池移除剩余数量）。lines 为关闭时的行快照 */
public class SalesOrderClosedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;
    private final String reason;
    private final List<SalesOrderLineInfo> lines;

    public SalesOrderClosedEvent(Long orderId, String orderNo, String reason, List<SalesOrderLineInfo> lines) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.reason = reason;
        this.lines = List.copyOf(lines);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public String getReason() { return reason; }
    public List<SalesOrderLineInfo> getLines() { return lines; }
}
