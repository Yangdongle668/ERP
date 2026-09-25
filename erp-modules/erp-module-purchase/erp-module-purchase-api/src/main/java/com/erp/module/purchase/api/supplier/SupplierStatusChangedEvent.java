package com.erp.module.purchase.api.supplier;

import com.erp.common.event.DomainEvent;

/** 供应商状态变化（准入通过、暂停、恢复、淘汰）后发布（同一事务内）。 */
public class SupplierStatusChangedEvent extends DomainEvent {

    private final Long supplierId;
    private final String supplierCode;
    private final SupplierStatus fromStatus;
    private final SupplierStatus toStatus;
    private final String reason;

    public SupplierStatusChangedEvent(Long supplierId, String supplierCode, SupplierStatus fromStatus, SupplierStatus toStatus, String reason) {
        this.supplierId = supplierId;
        this.supplierCode = supplierCode;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    public Long getSupplierId() { return supplierId; }
    public String getSupplierCode() { return supplierCode; }
    public SupplierStatus getFromStatus() { return fromStatus; }
    public SupplierStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
}
