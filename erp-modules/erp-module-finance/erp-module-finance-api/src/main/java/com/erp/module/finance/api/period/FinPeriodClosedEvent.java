package com.erp.module.finance.api.period;

import com.erp.common.event.DomainEvent;

/** 财务期间结账（reopened = true 表示反结账） */
public class FinPeriodClosedEvent extends DomainEvent {

    private final String period;
    private final boolean reopened;

    public FinPeriodClosedEvent(String period, boolean reopened) {
        this.period = period;
        this.reopened = reopened;
    }

    public String getPeriod() {
        return period;
    }

    public boolean isReopened() {
        return reopened;
    }
}
