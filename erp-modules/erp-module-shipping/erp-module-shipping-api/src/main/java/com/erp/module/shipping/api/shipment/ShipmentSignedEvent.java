package com.erp.module.shipping.api.shipment;

import com.erp.common.event.DomainEvent;
import java.time.LocalDateTime;

/** 出货完成：客户签收或登记提单（外销） */
public class ShipmentSignedEvent extends DomainEvent {

    private final Long shipmentId;
    private final String shipmentNo;
    private final Long customerId;
    private final LocalDateTime signedAt;

    public ShipmentSignedEvent(Long shipmentId, String shipmentNo, Long customerId, LocalDateTime signedAt) {
        this.shipmentId = shipmentId;
        this.shipmentNo = shipmentNo;
        this.customerId = customerId;
        this.signedAt = signedAt;
    }

    public Long getShipmentId() { return shipmentId; }
    public String getShipmentNo() { return shipmentNo; }
    public Long getCustomerId() { return customerId; }
    public LocalDateTime getSignedAt() { return signedAt; }
}
