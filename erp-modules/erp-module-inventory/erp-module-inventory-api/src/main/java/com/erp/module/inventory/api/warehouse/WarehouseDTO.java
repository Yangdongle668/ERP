package com.erp.module.inventory.api.warehouse;

/** 仓库对外视图。available 为仓库类型是否可用仓（待检、不良品、退货仓为 false）。 */
public record WarehouseDTO(Long id, String code, String name, WarehouseType warehouseType, boolean available,
                           boolean locationEnabled, boolean enabled, boolean isDefault) {
}
