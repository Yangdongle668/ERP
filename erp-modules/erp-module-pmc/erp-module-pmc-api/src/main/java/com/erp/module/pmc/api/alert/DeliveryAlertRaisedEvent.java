package com.erp.module.pmc.api.alert;

import com.erp.common.event.DomainEvent;
import java.time.LocalDate;

/** 交期预警新产生或级别升高（工作台预警计划员、业务员；CRITICAL 通知 PMC 主管） */
public class DeliveryAlertRaisedEvent extends DomainEvent {

    private final Long alertId;
    private final Long orderLineId;
    private final String orderNo;
    private final Long customerId;
    private final Long materialId;
    private final Long ownerId;
    private final LocalDate promisedDate;
    private final LocalDate estimatedDate;
    private final int delayDays;
    private final String alertLevel;
    private final String cause;
    private final String causeDetail;

    public DeliveryAlertRaisedEvent(Long alertId, Long orderLineId, String orderNo, Long customerId, Long materialId, Long ownerId, LocalDate promisedDate, LocalDate estimatedDate, int delayDays, String alertLevel, String cause, String causeDetail) {
        this.alertId = alertId;
        this.orderLineId = orderLineId;
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.materialId = materialId;
        this.ownerId = ownerId;
        this.promisedDate = promisedDate;
        this.estimatedDate = estimatedDate;
        this.delayDays = delayDays;
        this.alertLevel = alertLevel;
        this.cause = cause;
        this.causeDetail = causeDetail;
    }

    public Long getAlertId() { return alertId; }
    public Long getOrderLineId() { return orderLineId; }
    public String getOrderNo() { return orderNo; }
    public Long getCustomerId() { return customerId; }
    public Long getMaterialId() { return materialId; }
    public Long getOwnerId() { return ownerId; }
    public LocalDate getPromisedDate() { return promisedDate; }
    public LocalDate getEstimatedDate() { return estimatedDate; }
    public int getDelayDays() { return delayDays; }
    public String getAlertLevel() { return alertLevel; }
    public String getCause() { return cause; }
    public String getCauseDetail() { return causeDetail; }
}
