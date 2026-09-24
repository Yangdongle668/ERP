package com.erp.module.engineering.api.material;

/** 物料类型（需求文档 05）。FPC、电子料等是物料类别，不是物料类型。 */
public enum MaterialType {
    RAW("原材料"),
    SEMI_FINISHED("半成品"),
    FINISHED("成品"),
    PACKAGING("包材"),
    AUXILIARY("辅料"),
    PHANTOM("虚拟件");

    private final String label;

    MaterialType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
