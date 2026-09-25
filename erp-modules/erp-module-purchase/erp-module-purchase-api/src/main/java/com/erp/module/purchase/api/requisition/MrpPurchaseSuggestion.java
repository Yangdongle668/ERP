package com.erp.module.purchase.api.requisition;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 采购建议（PMC 确认后转采购申请）。
 *
 * @param qty          建议数量（基本单位）
 * @param plannerId    计划员：按计划员合并为一张申请单
 * @param supplierId   建议供应商，为空时取物料默认供应商
 * @param sourceDemand 需求来源说明，如“SO-202609-0001”
 */
public record MrpPurchaseSuggestion(Long mrpRunId, Long mrpResultId, Long materialId, BigDecimal qty, LocalDate requiredDate,
                                    Long plannerId, Long supplierId, String sourceDemand) {
}
