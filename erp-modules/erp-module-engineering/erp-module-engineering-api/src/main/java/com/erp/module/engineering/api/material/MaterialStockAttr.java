package com.erp.module.engineering.api.material;

import java.math.BigDecimal;

/** 库存属性（仓库使用）。minRemainingLifePct 为小数。 */
public record MaterialStockAttr(Long materialId, String baseUom, Tracking tracking, IssueRule issueRule, Integer shelfLifeDays,
                                BigDecimal minRemainingLifePct, BigDecimal safetyStock, BigDecimal maxStock,
                                BigDecimal unitNetWeight, BigDecimal unitGrossWeight) {
}
