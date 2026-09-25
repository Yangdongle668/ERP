package com.erp.module.purchase.api.order;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 采购订单审核通过后发布（同一事务内）；反审核时 approved = false。PMC 据此更新在途量。 */
public class PurchaseOrderApprovedEvent extends DomainEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long supplierId;
    private final boolean approved;
    private final List<Long> materialIds;

    public PurchaseOrderApprovedEvent(Long orderId, String orderNo, Long supplierId, boolean approved, List<Long> materialIds) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.supplierId = supplierId;
        this.approved = approved;
        this.materialIds = List.copyOf(materialIds);
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public Long getSupplierId() { return supplierId; }
    public boolean isApproved() { return approved; }
    public List<Long> getMaterialIds() { return materialIds; }
}
