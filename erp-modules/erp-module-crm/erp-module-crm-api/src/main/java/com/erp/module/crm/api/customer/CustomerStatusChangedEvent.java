package com.erp.module.crm.api.customer;

import com.erp.common.event.DomainEvent;

/** 客户状态变化（转正式、停用、启用、加入/移出黑名单） */
public class CustomerStatusChangedEvent extends DomainEvent {

    private final Long customerId;
    private final String customerCode;
    private final CustomerStatus fromStatus;
    private final CustomerStatus toStatus;
    private final String reason;

    public CustomerStatusChangedEvent(Long customerId, String customerCode, CustomerStatus fromStatus, CustomerStatus toStatus, String reason) {
        this.customerId = customerId;
        this.customerCode = customerCode;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    public Long getCustomerId() { return customerId; }
    public String getCustomerCode() { return customerCode; }
    public CustomerStatus getFromStatus() { return fromStatus; }
    public CustomerStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
}
