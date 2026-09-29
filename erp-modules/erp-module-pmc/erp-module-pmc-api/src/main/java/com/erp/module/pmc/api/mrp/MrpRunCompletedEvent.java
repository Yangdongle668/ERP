package com.erp.module.pmc.api.mrp;

import com.erp.common.event.DomainEvent;

/** MRP 运算完成（成功或失败） */
public class MrpRunCompletedEvent extends DomainEvent {

    private final Long runId;
    private final String runNo;
    private final String runStatus;
    private final int materialCount;
    private final int suggestionCount;
    private final Long operatorId;

    public MrpRunCompletedEvent(Long runId, String runNo, String runStatus, int materialCount, int suggestionCount, Long operatorId) {
        this.runId = runId;
        this.runNo = runNo;
        this.runStatus = runStatus;
        this.materialCount = materialCount;
        this.suggestionCount = suggestionCount;
        this.operatorId = operatorId;
    }

    public Long getRunId() { return runId; }
    public String getRunNo() { return runNo; }
    public String getRunStatus() { return runStatus; }
    public int getMaterialCount() { return materialCount; }
    public int getSuggestionCount() { return suggestionCount; }
    public Long getOperatorId() { return operatorId; }
}
