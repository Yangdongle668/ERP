package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存过账明细行。
 *
 * @param bizLineId  来源单据行 ID（用于幂等与追溯）
 * @param qty        基本单位数量，必须大于 0；方向由 {@link StockDirection} 表示
 * @param unitCost   入库单价（基本单位），出库时可为空，由仓库按成本方法计算
 * @param serialNos  序列号管理物料必填，个数等于数量
 */
public record StockPostingLine(
        Long bizLineId,
        Long materialId,
        Long warehouseId,
        Long locationId,
        String batchNo,
        BigDecimal qty,
        BigDecimal unitCost,
        List<String> serialNos) {
}
