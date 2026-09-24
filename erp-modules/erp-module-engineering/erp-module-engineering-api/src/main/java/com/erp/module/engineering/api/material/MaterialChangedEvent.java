package com.erp.module.engineering.api.material;

import com.erp.common.event.DomainEvent;

import java.util.Set;

/**
 * 物料计划/采购/库存属性变化（ENG-MAT-R12）。PMC 可据此标记 MRP 需要重算，仓库可刷新缓存。
 *
 * @param changedGroups 变化的属性组：PLAN / PURCHASE / STOCK / QUALITY / FINANCE
 */
public class MaterialChangedEvent extends DomainEvent {

    public static final String PLAN = "PLAN";
    public static final String PURCHASE = "PURCHASE";
    public static final String STOCK = "STOCK";
    public static final String QUALITY = "QUALITY";
    public static final String FINANCE = "FINANCE";

    private final Long materialId;
    private final String materialCode;
    private final Set<String> changedGroups;

    public MaterialChangedEvent(Long materialId, String materialCode, Set<String> changedGroups) {
        this.materialId = materialId;
        this.materialCode = materialCode;
        this.changedGroups = Set.copyOf(changedGroups);
    }

    public Long getMaterialId() {
        return materialId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public Set<String> getChangedGroups() {
        return changedGroups;
    }
}
