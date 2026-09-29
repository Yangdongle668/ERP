package com.erp.module.production.api.order;

import com.erp.common.event.DomainEvent;

/** 生产订单关闭（财务成本结算、PMC 释放在制） */
public class ProductionOrderClosedEvent extends DomainEvent {

    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final String reason;

    public ProductionOrderClosedEvent(Long prodOrderId, String prodOrderNo, Long materialId, String reason) {
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.reason = reason;
    }

    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public String getReason() { return reason; }
}
