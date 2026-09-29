package com.erp.module.quality.api.scar;

import com.erp.common.event.DomainEvent;

/** SCAR 结案（资材供应商评估参考） */
public class ScarClosedEvent extends DomainEvent {

    private final Long scarId;
    private final String scarNo;
    private final Long supplierId;
    private final Long materialId;
    private final String verifyResult;
    private final int invalidCount;
    private final boolean overdue;

    public ScarClosedEvent(Long scarId, String scarNo, Long supplierId, Long materialId, String verifyResult, int invalidCount, boolean overdue) {
        this.scarId = scarId;
        this.scarNo = scarNo;
        this.supplierId = supplierId;
        this.materialId = materialId;
        this.verifyResult = verifyResult;
        this.invalidCount = invalidCount;
        this.overdue = overdue;
    }

    public Long getScarId() { return scarId; }
    public String getScarNo() { return scarNo; }
    public Long getSupplierId() { return supplierId; }
    public Long getMaterialId() { return materialId; }
    public String getVerifyResult() { return verifyResult; }
    public int getInvalidCount() { return invalidCount; }
    public boolean getOverdue() { return overdue; }
}
