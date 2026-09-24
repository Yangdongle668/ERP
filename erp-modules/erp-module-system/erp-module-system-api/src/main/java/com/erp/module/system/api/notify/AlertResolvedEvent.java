package com.erp.module.system.api.notify;

import com.erp.common.event.DomainEvent;

/** 预警条件消除，自动关闭预警。 */
public class AlertResolvedEvent extends DomainEvent {

    private final String alertKey;

    public AlertResolvedEvent(String alertKey) {
        this.alertKey = alertKey;
    }

    public String getAlertKey() {
        return alertKey;
    }
}
