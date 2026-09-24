package com.erp.module.inventory.api.stock;

import com.erp.common.event.DomainEvent;

import java.util.Set;

/** 库存发生变动（过账事务内发布），供预警等 AFTER_COMMIT 监听 */
public class StockChangedEvent extends DomainEvent {

    private final Set<Long> materialIds;

    public StockChangedEvent(Set<Long> materialIds) {
        this.materialIds = Set.copyOf(materialIds);
    }

    public Set<Long> getMaterialIds() {
        return materialIds;
    }
}
