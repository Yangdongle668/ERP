package com.erp.module.production.api.report;

import com.erp.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 报工反审核（与审核事件数量相同，监听方扣回） */
public class WorkReportReversedEvent extends DomainEvent {

    private final Long reportId;
    private final String reportNo;
    private final Long prodOrderId;
    private final String prodOrderNo;
    private final Long materialId;
    private final int operationSeq;
    private final Long workCenterId;
    private final Long toolingId;
    private final LocalDate reportDate;
    private final BigDecimal goodQty;
    private final BigDecimal defectQty;
    private final BigDecimal scrapQty;
    private final BigDecimal workHours;
    private final BigDecimal machineHours;

    public WorkReportReversedEvent(Long reportId, String reportNo, Long prodOrderId, String prodOrderNo, Long materialId, int operationSeq, Long workCenterId, Long toolingId, LocalDate reportDate, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty, BigDecimal workHours, BigDecimal machineHours) {
        this.reportId = reportId;
        this.reportNo = reportNo;
        this.prodOrderId = prodOrderId;
        this.prodOrderNo = prodOrderNo;
        this.materialId = materialId;
        this.operationSeq = operationSeq;
        this.workCenterId = workCenterId;
        this.toolingId = toolingId;
        this.reportDate = reportDate;
        this.goodQty = goodQty;
        this.defectQty = defectQty;
        this.scrapQty = scrapQty;
        this.workHours = workHours;
        this.machineHours = machineHours;
    }

    public Long getReportId() { return reportId; }
    public String getReportNo() { return reportNo; }
    public Long getProdOrderId() { return prodOrderId; }
    public String getProdOrderNo() { return prodOrderNo; }
    public Long getMaterialId() { return materialId; }
    public int getOperationSeq() { return operationSeq; }
    public Long getWorkCenterId() { return workCenterId; }
    public Long getToolingId() { return toolingId; }
    public LocalDate getReportDate() { return reportDate; }
    public BigDecimal getGoodQty() { return goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public BigDecimal getScrapQty() { return scrapQty; }
    public BigDecimal getWorkHours() { return workHours; }
    public BigDecimal getMachineHours() { return machineHours; }
}
