package com.erp.module.production.api.order;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;

/** 生产订单完工（合格入库 + 报废 ≥ 计划数量）；样品订单由研发工程样品单监听 */
public class ProductionOrderCompletedEvent extends DomainEvent {

    private final Long prodOrderId;
    private final String prodOrderNo;
    private final String orderType;
    private final Long materialId;
    private final String sourceType;
    private final Long sourceId;
    private final BigDecimal qualifiedStockedQty;

    public ProductionOrderCompletedEvent(Long prodOrderId, String prodOrderNo, String orderType, Long materialId, String sourceType, Long sourceId, BigDecimal qualifiedStockedQty) {
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.orderType = orderType;
        this.materialId = materialId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.qualifiedStockedQty = qualifiedStockedQty;
    }

    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public String getOrderType() { return orderType; }
    public Long getMaterialId() { return materialId; }
    public String getSourceType() { return sourceType; }
    public Long getSourceId() { return sourceId; }
    public BigDecimal getQualifiedStockedQty() { return qualifiedStockedQty; }
}
