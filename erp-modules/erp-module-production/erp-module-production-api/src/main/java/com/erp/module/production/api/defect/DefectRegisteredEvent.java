package com.erp.module.production.api.defect;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 报工登记不良后发布（品质按阈值提醒 QE） */
public class DefectRegisteredEvent extends DomainEvent {

    private final Long defectId;
    private final Long reportId;
    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final int operationSeq;
    private final String defectCode;
    private final BigDecimal qty;
    private final LocalDate reportDate;

    public DefectRegisteredEvent(Long defectId, Long reportId, Long prodOrderId, String prodOrderNo, Long materialId, int operationSeq, String defectCode, BigDecimal qty, LocalDate reportDate) {
        this.defectId = defectId;
        this.reportId = reportId;
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.operationSeq = operationSeq;
        this.defectCode = defectCode;
        this.qty = qty;
        this.reportDate = reportDate;
    }

    public Long getDefectId() { return defectId; }
    public Long getReportId() { return reportId; }
    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public int getOperationSeq() { return operationSeq; }
    public String getDefectCode() { return defectCode; }
    public BigDecimal getQty() { return qty; }
    public LocalDate getReportDate() { return reportDate; }
}
