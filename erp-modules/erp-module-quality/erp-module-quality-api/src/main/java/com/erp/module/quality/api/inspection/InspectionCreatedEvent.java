package com.erp.module.quality.api.inspection;

import com.erp.common.event.DomainEvent;
import java.math.BigDecimal;

/** 检验单生成（事件触发或手工新建） */
public class InspectionCreatedEvent extends DomainEvent {

    private final Long inspectionId;
    private final String inspectionNo;
    private final String inspectType;
    private final Long materialId;
    private final String batchNo;
    private final BigDecimal lotQty;
    private final String upstreamType;
    private final Long upstreamId;
    private final String upstreamNo;

    public InspectionCreatedEvent(Long inspectionId, String inspectionNo, String inspectType, Long materialId, String batchNo, BigDecimal lotQty, String upstreamType, Long upstreamId, String upstreamNo) {
        this.inspectionId = inspectionId;
        this.inspectionNo = inspectionNo;
        this.inspectType = inspectType;
        this.materialId = materialId;
        this.batchNo = batchNo;
        this.lotQty = lotQty;
        this.upstreamType = upstreamType;
        this.upstreamId = upstreamId;
        this.upstreamNo = upstreamNo;
    }

    public Long getInspectionId() { return inspectionId; }
    public String getInspectionNo() { return inspectionNo; }
    public String getInspectType() { return inspectType; }
    public Long getMaterialId() { return materialId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getLotQty() { return lotQty; }
    public String getUpstreamType() { return upstreamType; }
    public Long getUpstreamId() { return upstreamId; }
    public String getUpstreamNo() { return upstreamNo; }
}
