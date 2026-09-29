package com.erp.module.finance.api.cost;

import com.erp.common.event.DomainEvent;

/** 成本计算锁定后发布：仓库回填流水成本、BI 更新毛利 */
public class CostCalculatedEvent extends DomainEvent {

    private final String period;

    public CostCalculatedEvent(String period) {
        this.period = period;
    }

    public String getPeriod() {
        return period;
    }
}
