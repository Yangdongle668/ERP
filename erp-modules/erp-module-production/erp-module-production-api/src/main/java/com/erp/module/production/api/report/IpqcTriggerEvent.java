package com.erp.module.production.api.report;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;

/** 检验点工序报工审核后发布，品质生成 IPQC 检验单 */
public class IpqcTriggerEvent extends DomainEvent {

    private final Long reportId;
    private final String reportNo;
    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final String batchNo;
    private final int operationSeq;
    private final Long workCenterId;
    private final BigDecimal qty;

    public IpqcTriggerEvent(Long reportId, String reportNo, Long prodOrderId, String prodOrderNo, Long materialId, String batchNo, int operationSeq, Long workCenterId, BigDecimal qty) {
        this.reportId = reportId;
        this.reportNo = reportNo;
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.batchNo = batchNo;
        this.operationSeq = operationSeq;
        this.workCenterId = workCenterId;
        this.qty = qty;
    }

    public Long getReportId() { return reportId; }
    public String getReportNo() { return reportNo; }
    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public String getBatchNo() { return batchNo; }
    public int getOperationSeq() { return operationSeq; }
    public Long getWorkCenterId() { return workCenterId; }
    public BigDecimal getQty() { return qty; }
}
