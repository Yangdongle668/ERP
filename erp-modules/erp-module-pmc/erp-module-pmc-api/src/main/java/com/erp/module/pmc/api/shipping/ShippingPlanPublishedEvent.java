package com.erp.module.pmc.api.shipping;

import com.erp.common.event.DomainEvent;
import java.util.List;

/** 出货计划发布（出货模块据此生成出货通知） */
public class ShippingPlanPublishedEvent extends DomainEvent {

    private final Long planId;
    private final String planNo;
    private final String planWeek;
    private final List<Long> orderLineIds;

    public ShippingPlanPublishedEvent(Long planId, String planNo, String planWeek, List<Long> orderLineIds) {
        this.planId = planId;
        this.planNo = planNo;
        this.planWeek = planWeek;
        this.orderLineIds = List.copyOf(orderLineIds);
    }

    public Long getPlanId() { return planId; }
    public String getPlanNo() { return planNo; }
    public String getPlanWeek() { return planWeek; }
    public List<Long> getOrderLineIds() { return orderLineIds; }
}
