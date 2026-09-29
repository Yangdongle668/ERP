package com.erp.module.production.api.order;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生产订单下达：用料与工序已固化（PMC 更新在制与分配量） */
public class ProductionOrderReleasedEvent extends DomainEvent {

    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final BigDecimal qty;
    private final LocalDate planEnd;

    public ProductionOrderReleasedEvent(Long prodOrderId, String prodOrderNo, Long materialId, BigDecimal qty, LocalDate planEnd) {
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.qty = qty;
        this.planEnd = planEnd;
    }

    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public BigDecimal getQty() { return qty; }
    public LocalDate getPlanEnd() { return planEnd; }
}
