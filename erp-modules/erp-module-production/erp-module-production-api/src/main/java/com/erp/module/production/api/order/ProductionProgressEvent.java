package com.erp.module.production.api.order;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;

/** 生产进度变化（报工审核/反审核、完工入库后发布） */
public class ProductionProgressEvent extends DomainEvent {

    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final BigDecimal completedQty;
    private final BigDecimal stockedQty;
    private final BigDecimal qualifiedStockedQty;

    public ProductionProgressEvent(Long prodOrderId, String prodOrderNo, Long materialId, BigDecimal completedQty, BigDecimal stockedQty, BigDecimal qualifiedStockedQty) {
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.completedQty = completedQty;
        this.stockedQty = stockedQty;
        this.qualifiedStockedQty = qualifiedStockedQty;
    }

    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public BigDecimal getCompletedQty() { return completedQty; }
    public BigDecimal getStockedQty() { return stockedQty; }
    public BigDecimal getQualifiedStockedQty() { return qualifiedStockedQty; }
}
