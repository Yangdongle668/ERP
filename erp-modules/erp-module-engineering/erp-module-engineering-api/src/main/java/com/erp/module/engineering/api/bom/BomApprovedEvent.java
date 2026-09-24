package com.erp.module.engineering.api.bom;

import com.erp.common.event.DomainEvent;

/** BOM 审核通过（PMC 标记 MRP 需重算，财务可重新卷算成本） */
public class BomApprovedEvent extends DomainEvent {

    private final Long bomId;
    private final Long materialId;
    private final int bomVersion;

    public BomApprovedEvent(Long bomId, Long materialId, int bomVersion) {
        this.bomId = bomId;
        this.materialId = materialId;
        this.bomVersion = bomVersion;
    }

    public Long getBomId() {
        return bomId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public int getBomVersion() {
        return bomVersion;
    }
}
