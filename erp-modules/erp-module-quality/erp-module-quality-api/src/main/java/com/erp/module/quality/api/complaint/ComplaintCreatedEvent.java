package com.erp.module.quality.api.complaint;

import com.erp.common.event.DomainEvent;

/** 客诉登记 */
public class ComplaintCreatedEvent extends DomainEvent {

    private final Long complaintId;
    private final String complaintNo;
    private final Long customerId;
    private final Long materialId;
    private final String severity;
    private final String complaintType;

    public ComplaintCreatedEvent(Long complaintId, String complaintNo, Long customerId, Long materialId, String severity, String complaintType) {
        this.complaintId = complaintId;
        this.complaintNo = complaintNo;
        this.customerId = customerId;
        this.materialId = materialId;
        this.severity = severity;
        this.complaintType = complaintType;
    }

    public Long getComplaintId() { return complaintId; }
    public String getComplaintNo() { return complaintNo; }
    public Long getCustomerId() { return customerId; }
    public Long getMaterialId() { return materialId; }
    public String getSeverity() { return severity; }
    public String getComplaintType() { return complaintType; }
}
