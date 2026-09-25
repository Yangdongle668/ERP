package com.erp.module.crm.api.customer;

import com.erp.common.event.DomainEvent;

import java.util.List;

/**
 * 客户转移（需求 03-01 3.4）：transferDocs 为 true 时，销售模块把这些客户未完成报价、订单的业务员改为新负责人。
 */
public class CustomerOwnerChangedEvent extends DomainEvent {

    private final List<Long> customerIds;
    private final Long newOwnerId;
    private final boolean transferDocs;

    public CustomerOwnerChangedEvent(List<Long> customerIds, Long newOwnerId, boolean transferDocs) {
        this.customerIds = List.copyOf(customerIds);
        this.newOwnerId = newOwnerId;
        this.transferDocs = transferDocs;
    }

    public List<Long> getCustomerIds() { return customerIds; }
    public Long getNewOwnerId() { return newOwnerId; }
    public boolean isTransferDocs() { return transferDocs; }
}
