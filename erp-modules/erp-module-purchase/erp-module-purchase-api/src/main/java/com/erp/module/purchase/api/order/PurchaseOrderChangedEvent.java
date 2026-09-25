package com.erp.module.purchase.api.order;

import com.erp.common.event.DomainEvent;

import java.util.List;

/**
 * 采购订单变更单审核生效、订单关闭后发布（同一事务内）。
 *
 * @param changeType CHANGE 变更生效 / CLOSE 关闭
 */
public class PurchaseOrderChangedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;
    private final int orderVersion;
    private final String changeType;
    private final List<Long> materialIds;

    public PurchaseOrderChangedEvent(Long orderId, String orderNo, int orderVersion, String changeType, List<Long> materialIds) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.orderVersion = orderVersion;
        this.changeType = changeType;
        this.materialIds = List.copyOf(materialIds);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public int getOrderVersion() { return orderVersion; }
    public String getChangeType() { return changeType; }
    public List<Long> getMaterialIds() { return materialIds; }
}
