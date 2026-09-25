package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

/** 销售订单反审核（PMC 删除对应需求） */
public class SalesOrderUnapprovedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;

    public SalesOrderUnapprovedEvent(Long orderId, String orderNo) {
        this.orderId = orderId;
        this.orderNo = orderNo;
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
}
