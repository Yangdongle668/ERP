package com.erp.module.inventory.api.warehouse;

import java.util.List;
import java.util.Optional;

/** 仓库查询（需求 08-仓库/01 第 5 节）。 */
public interface WarehouseApi {

    Optional<WarehouseDTO> get(Long id);

    /**
     * 物料的默认仓：沿物料类别向上查找第一个有映射的类别 → 按物料类型取该类型的默认仓；
     * 都找不到时抛出“物料「{code}」没有默认仓库，请在仓库管理中配置类别默认仓”。
     *
     * @param warehouseType 指定时直接返回该类型的默认仓（如待检仓 QC）
     */
    WarehouseDTO getDefaultWarehouse(Long materialId, WarehouseType warehouseType);

    /** 某类型的启用仓库 */
    List<WarehouseDTO> listByType(WarehouseType type);
}
