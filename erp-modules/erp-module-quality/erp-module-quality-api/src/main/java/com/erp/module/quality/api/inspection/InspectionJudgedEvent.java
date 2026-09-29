package com.erp.module.quality.api.inspection;

import com.erp.common.event.DomainEvent;
import java.math.BigDecimal;

/** 检验判定完成（同一事务内同步发布）。inspectType：IQC/IPQC/FQC/OQC/RETURN/RECHECK；result：QUALIFIED/REJECTED/CONCESSION/SORTED；
 * upstreamType/upstreamId/upstreamLineId 为上游业务单据（到货单行、完工入库申请、销售退货行、出货通知行、报工单）。数量为基本单位 */
public class InspectionJudgedEvent extends DomainEvent {

    private final Long inspectionId;
    private final String inspectionNo;
    private final String inspectType;
    private final Long materialId;
    private final String batchNo;
    private final String upstreamType;
    private final Long upstreamId;
    private final Long upstreamLineId;
    private final String upstreamNo;
    private final BigDecimal lotQty;
    private final BigDecimal qualifiedQty;
    private final BigDecimal concessionQty;
    private final BigDecimal rejectedQty;
    private final String result;

    public InspectionJudgedEvent(Long inspectionId, String inspectionNo, String inspectType, Long materialId, String batchNo, String upstreamType, Long upstreamId, Long upstreamLineId, String upstreamNo, BigDecimal lotQty, BigDecimal qualifiedQty, BigDecimal concessionQty, BigDecimal rejectedQty, String result) {
        this.inspectionId = inspectionId;
        this.inspectionNo = inspectionNo;
        this.inspectType = inspectType;
        this.materialId = materialId;
        this.batchNo = batchNo;
        this.upstreamType = upstreamType;
        this.upstreamId = upstreamId;
        this.upstreamLineId = upstreamLineId;
        this.upstreamNo = upstreamNo;
        this.lotQty = lotQty;
        this.qualifiedQty = qualifiedQty;
        this.concessionQty = concessionQty;
        this.rejectedQty = rejectedQty;
        this.result = result;
    }

    public Long getInspectionId() { return inspectionId; }
    public String getInspectionNo() { return inspectionNo; }
    public String getInspectType() { return inspectType; }
    public Long getMaterialId() { return materialId; }
    public String getBatchNo() { return batchNo; }
    public String getUpstreamType() { return upstreamType; }
    public Long getUpstreamId() { return upstreamId; }
    public Long getUpstreamLineId() { return upstreamLineId; }
    public String getUpstreamNo() { return upstreamNo; }
    public BigDecimal getLotQty() { return lotQty; }
    public BigDecimal getQualifiedQty() { return qualifiedQty; }
    public BigDecimal getConcessionQty() { return concessionQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public String getResult() { return result; }
}
