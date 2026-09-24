package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;

/**
 * 物料库存汇总（全部仓库，基本单位）。
 *
 * @param onHandQty    现存量（全部仓库）
 * @param availableQty 可用量 = 可用仓未冻结未过期现存量 − 有效预留
 * @param qcQty        待检仓数量
 * @param ngQty        不良品仓数量
 */
public record StockSummary(Long materialId, BigDecimal onHandQty, BigDecimal availableQty, BigDecimal reservedQty,
                           BigDecimal qcQty, BigDecimal ngQty) {
}
