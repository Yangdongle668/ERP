package com.erp.module.purchase.api.requisition;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 采购申请关闭后发布（PUR-REQ-R06），PMC 下次运算重新计算这些物料。 */
public class PurchaseRequisitionClosedEvent extends DomainEvent {

    private final Long requisitionId;
    private final String requisitionNo;
    private final List<Long> mrpResultIds;
    private final List<Long> materialIds;

    public PurchaseRequisitionClosedEvent(Long requisitionId, String requisitionNo, List<Long> mrpResultIds, List<Long> materialIds) {
        this.requisitionId = requisitionId;
        this.requisitionNo = requisitionNo;
        this.mrpResultIds = List.copyOf(mrpResultIds);
        this.materialIds = List.copyOf(materialIds);
    }

    public Long getRequisitionId() { return requisitionId; }
    public String getRequisitionNo() { return requisitionNo; }
    public List<Long> getMrpResultIds() { return mrpResultIds; }
    public List<Long> getMaterialIds() { return materialIds; }
}
