package com.erp.module.inventory.api.period;

import com.erp.common.event.DomainEvent;

/** 库存期间月结后发布，财务模块据此进行成本计算 */
public class PeriodClosedEvent extends DomainEvent {

    private final String period;

    public PeriodClosedEvent(String period) {
        this.period = period;
    }

    public String getPeriod() {
        return period;
    }
}
