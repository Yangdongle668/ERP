package com.erp.module.engineering.api.material;

import com.erp.common.event.DomainEvent;

/** 物料启用/停用事件。例如采购、销售可监听停用事件以提示在途单据。 */
public class MaterialStatusChangedEvent extends DomainEvent {

    private final Long materialId;
    private final String materialCode;
    private final MaterialStatus oldStatus;
    private final MaterialStatus newStatus;

    public MaterialStatusChangedEvent(Long materialId, String materialCode, MaterialStatus oldStatus, MaterialStatus newStatus) {
        this.materialId = materialId;
        this.materialCode = materialCode;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public MaterialStatus getOldStatus() {
        return oldStatus;
    }

    public MaterialStatus getNewStatus() {
        return newStatus;
    }
}
