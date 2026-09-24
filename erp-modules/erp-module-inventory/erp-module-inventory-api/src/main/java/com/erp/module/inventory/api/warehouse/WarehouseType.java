package com.erp.module.inventory.api.warehouse;

/**
 * 仓库类型（需求文档 08 第 2 节）。
 *
 * <p>{@link #available} 表示该类仓库中的库存是否可被 MRP、生产领料、销售出库使用。
 * 待检仓、不良品仓、退货仓的库存不可用。
 */
public enum WarehouseType {
    RAW("原材料仓", true),
    SEMI("半成品仓", true),
    FG("成品仓", true),
    FPC("FPC仓", true),
    ELEC("电子料仓", true),
    PKG("包材仓", true),
    AUX("辅料仓", true),
    NG("不良品仓", false),
    QC("待检仓", false),
    RTN("退货仓", false);

    private final String label;
    private final boolean available;

    WarehouseType(String label, boolean available) {
        this.label = label;
        this.available = available;
    }

    public String label() {
        return label;
    }

    public boolean available() {
        return available;
    }
}
