package com.erp.module.inventory.dal.dataobject;

import lombok.Data;

/** inv_warehouse_user 的一行（技术表，无审计字段） */
@Data
public class WarehouseUserRow {
    private Long warehouseId;
    private Long userId;
}
