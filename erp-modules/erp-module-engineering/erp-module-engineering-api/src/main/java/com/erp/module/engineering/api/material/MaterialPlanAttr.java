package com.erp.module.engineering.api.material;

import java.math.BigDecimal;

/** 计划属性（PMC/MRP 使用）。数量均为基本单位。 */
public record MaterialPlanAttr(Long materialId, SourceType sourceType, int leadTimeDays, BigDecimal safetyStock, BigDecimal maxStock,
                               OrderPolicy orderPolicy, BigDecimal fixedLotQty, Integer periodDays, BigDecimal moq, BigDecimal mpq,
                               Long plannerId, int lowLevelCode) {
}
