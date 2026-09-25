package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 订单行已出货数量变化（出货确认、冲销、换货退货），PMC 据此更新需求已满足数量。lines 为变化后的行快照 */
public class SalesOrderShipmentChangedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;
    private final List<SalesOrderLineInfo> lines;

    public SalesOrderShipmentChangedEvent(Long orderId, String orderNo, List<SalesOrderLineInfo> lines) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.lines = List.copyOf(lines);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public List<SalesOrderLineInfo> getLines() { return lines; }
}
