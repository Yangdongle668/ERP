package com.erp.module.quality.api.complaint;

import com.erp.common.event.DomainEvent;
import java.math.BigDecimal;

/** 客诉结案且同意赔偿金额 > 0（财务登记应收折让） */
public class ComplaintClaimAgreedEvent extends DomainEvent {

    private final Long complaintId;
    private final String complaintNo;
    private final Long customerId;
    private final String currency;
    private final BigDecimal agreedAmount;
    private final String handling;

    public ComplaintClaimAgreedEvent(Long complaintId, String complaintNo, Long customerId, String currency, BigDecimal agreedAmount, String handling) {
        this.complaintId = complaintId;
        this.complaintNo = complaintNo;
        this.customerId = customerId;
        this.currency = currency;
        this.agreedAmount = agreedAmount;
        this.handling = handling;
    }

    public Long getComplaintId() { return complaintId; }
    public String getComplaintNo() { return complaintNo; }
    public Long getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getAgreedAmount() { return agreedAmount; }
    public String getHandling() { return handling; }
}
