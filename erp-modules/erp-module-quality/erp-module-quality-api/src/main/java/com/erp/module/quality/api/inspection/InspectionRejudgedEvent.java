package com.erp.module.quality.api.inspection;

import com.erp.common.event.DomainEvent;

/** 检验重判审批通过：原判定撤销，检验单回到检验中（上游据此扣回判定数量） */
public class InspectionRejudgedEvent extends DomainEvent {

    private final Long inspectionId;
    private final String inspectionNo;
    private final String inspectType;
    private final String upstreamType;
    private final Long upstreamId;
    private final Long upstreamLineId;
    private final String previousResult;

    public InspectionRejudgedEvent(Long inspectionId, String inspectionNo, String inspectType, String upstreamType, Long upstreamId, Long upstreamLineId, String previousResult) {
        this.inspectionId = inspectionId;
        this.inspectionNo = inspectionNo;
        this.inspectType = inspectType;
        this.upstreamType = upstreamType;
        this.upstreamId = upstreamId;
        this.upstreamLineId = upstreamLineId;
        this.previousResult = previousResult;
    }

    public Long getInspectionId() { return inspectionId; }
    public String getInspectionNo() { return inspectionNo; }
    public String getInspectType() { return inspectType; }
    public String getUpstreamType() { return upstreamType; }
    public Long getUpstreamId() { return upstreamId; }
    public Long getUpstreamLineId() { return upstreamLineId; }
    public String getPreviousResult() { return previousResult; }
}
