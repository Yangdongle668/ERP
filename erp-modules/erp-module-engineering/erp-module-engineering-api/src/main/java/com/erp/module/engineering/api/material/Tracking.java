package com.erp.module.engineering.api.material;

/** 库存管理方式：不管理 / 批次 / 序列号 */
public enum Tracking {
    NONE("不管理"), BATCH("批次"), SERIAL("序列号");

    private final String label;

    Tracking(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
