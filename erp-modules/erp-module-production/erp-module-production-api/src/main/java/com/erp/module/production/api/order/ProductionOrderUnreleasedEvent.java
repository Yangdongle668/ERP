package com.erp.module.production.api.order;

import com.erp.common.event.DomainEvent;

/** 生产订单撤销下达（PMC 移除在制与分配量） */
public class ProductionOrderUnreleasedEvent extends DomainEvent {

    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;

    public ProductionOrderUnreleasedEvent(Long prodOrderId, String prodOrderNo, Long materialId) {
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
    }

    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
}
