package com.erp.module.finance.api.receivable;

import com.erp.common.event.DomainEvent;

import java.util.List;

/** 客户应收余额变化（应收确认 / 作废、核销 / 反核销、预收确认）：CRM 刷新信用占用 */
public class ReceivableBalanceChangedEvent extends DomainEvent {

    private final List<Long> customerIds;

    public ReceivableBalanceChangedEvent(List<Long> customerIds) {
        this.customerIds = customerIds == null ? List.of() : List.copyOf(customerIds);
    }

    public List<Long> getCustomerIds() {
        return customerIds;
    }
}
