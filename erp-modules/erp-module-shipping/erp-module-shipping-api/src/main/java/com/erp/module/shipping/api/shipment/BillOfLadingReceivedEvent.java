package com.erp.module.shipping.api.shipment;

import com.erp.common.event.DomainEvent;
import java.time.LocalDate;

/** 登记提单日期（回款计划“提单日”节点到期日） */
public class BillOfLadingReceivedEvent extends DomainEvent {

    private final Long shipmentId;
    private final String shipmentNo;
    private final Long customerId;
    private final String blNo;
    private final LocalDate blDate;

    public BillOfLadingReceivedEvent(Long shipmentId, String shipmentNo, Long customerId, String blNo, LocalDate blDate) {
        this.shipmentId = shipmentId;
        this.shipmentNo = shipmentNo;
        this.customerId = customerId;
        this.blNo = blNo;
        this.blDate = blDate;
    }

    public Long getShipmentId() { return shipmentId; }
    public String getShipmentNo() { return shipmentNo; }
    public Long getCustomerId() { return customerId; }
    public String getBlNo() { return blNo; }
    public LocalDate getBlDate() { return blDate; }
}
