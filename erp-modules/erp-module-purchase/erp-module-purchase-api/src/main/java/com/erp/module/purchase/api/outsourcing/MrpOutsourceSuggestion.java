package com.erp.module.purchase.api.outsourcing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 委外建议。
 *
 * @param qty        委外数量（基本单位）
 * @param supplierId 加工商，为空时取物料默认供应商
 */
public record MrpOutsourceSuggestion(Long mrpResultId, Long materialId, BigDecimal qty, LocalDate requiredDate, Long supplierId) {
}
