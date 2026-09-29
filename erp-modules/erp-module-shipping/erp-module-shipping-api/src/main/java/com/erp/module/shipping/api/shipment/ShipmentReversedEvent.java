package com.erp.module.shipping.api.shipment;

import com.erp.common.event.DomainEvent;

/** 出货冲销（仓库反确认销售出库后）：财务冲回应收 */
public class ShipmentReversedEvent extends DomainEvent {

    private final Long shipmentId;
    private final String shipmentNo;
    private final Long customerId;
    private final String reason;

    public ShipmentReversedEvent(Long shipmentId, String shipmentNo, Long customerId, String reason) {
        this.shipmentId = shipmentId;
        this.shipmentNo = shipmentNo;
        this.customerId = customerId;
        this.reason = reason;
    }

    public Long getShipmentId() { return shipmentId; }
    public String getShipmentNo() { return shipmentNo; }
    public Long getCustomerId() { return customerId; }
    public String getReason() { return reason; }
}
