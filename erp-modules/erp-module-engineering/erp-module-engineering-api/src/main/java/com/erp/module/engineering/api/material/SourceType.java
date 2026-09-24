package com.erp.module.engineering.api.material;

/** 取得方式：采购 / 自制 / 委外 */
public enum SourceType {
    PURCHASE("采购"), MAKE("自制"), OUTSOURCE("委外");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
