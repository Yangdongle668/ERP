package com.erp.module.sales.api.order;

import com.erp.common.event.DomainEvent;

/** 客户未出货订单金额变化（审核、反审核、变更、出货、关闭），CRM 据此刷新信用占用 */
public class SalesOrderOpenAmountChangedEvent extends DomainEvent {

    private final Long customerId;
    private final Long orderId;

    public SalesOrderOpenAmountChangedEvent(Long customerId, Long orderId) {
        this.customerId = customerId;
        this.orderId = orderId;
    }

    public Long getCustomerId() { return customerId; }
    public Long getOrderId() { return orderId; }
}
