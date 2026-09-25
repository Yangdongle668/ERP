package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 销售订单审核通过（PMC 需求池、CRM 商机与最近下单日期） */
public class SalesOrderApprovedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;
    private final String orderType;
    private final Long customerId;
    private final Long ownerId;
    private final List<SalesOrderLineInfo> lines;

    public SalesOrderApprovedEvent(Long orderId, String orderNo, String orderType, Long customerId, Long ownerId, List<SalesOrderLineInfo> lines) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.orderType = orderType;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.lines = List.copyOf(lines);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public String getOrderType() { return orderType; }
    public Long getCustomerId() { return customerId; }
    public Long getOwnerId() { return ownerId; }
    public List<SalesOrderLineInfo> getLines() { return lines; }
}
