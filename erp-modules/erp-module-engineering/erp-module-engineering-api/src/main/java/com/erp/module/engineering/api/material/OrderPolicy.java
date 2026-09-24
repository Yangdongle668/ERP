package com.erp.module.engineering.api.material;

/** MRP 批量规则：按需 / 固定批量 / 按周期合并 */
public enum OrderPolicy {
    LOT_FOR_LOT("按需"), FIXED_QTY("固定批量"), PERIOD("按周期");

    private final String label;

    OrderPolicy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
