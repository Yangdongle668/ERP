package com.erp.module.engineering.api.bom;

import com.erp.common.event.DomainEvent;

/** 父件的默认 BOM 版本变化（oldBomId 为空表示之前没有默认版本） */
public class BomDefaultChangedEvent extends DomainEvent {

    private final Long materialId;
    private final Long oldBomId;
    private final Long newBomId;

    public BomDefaultChangedEvent(Long materialId, Long oldBomId, Long newBomId) {
        this.materialId = materialId;
        this.oldBomId = oldBomId;
        this.newBomId = newBomId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public Long getOldBomId() {
        return oldBomId;
    }

    public Long getNewBomId() {
        return newBomId;
    }
}
