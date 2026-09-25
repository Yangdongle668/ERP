package com.erp.module.sales.api.quotation;

import com.erp.common.event.DomainEvent;

/** 报价单保存（新建）后发布（SAL-QT-R07）；关联商机时 CRM 推进商机阶段 */
public class QuotationCreatedEvent extends DomainEvent {

    private final Long quotationId;
    private final String quotationNo;
    private final Long customerId;
    private final Long opportunityId;

    public QuotationCreatedEvent(Long quotationId, String quotationNo, Long customerId, Long opportunityId) {
        this.quotationId = quotationId;
        this.quotationNo = quotationNo;
        this.customerId = customerId;
        this.opportunityId = opportunityId;
    }

    public Long getQuotationId() { return quotationId; }
    public String getQuotationNo() { return quotationNo; }
    public Long getCustomerId() { return customerId; }
    public Long getOpportunityId() { return opportunityId; }
}
