package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PMC（MRP）生产建议，转为“已计划”的生产订单（MFG-MO-R09）。
 *
 * @param suggestionId     建议 ID（记为订单来源）
 * @param qty              计划数量（基本单位）
 * @param plannerId        计划员；为空取当前用户
 * @param deptId           生产车间；为空按首道工序工作中心所属部门
 * @param salesOrderLineId 对应的销售订单行（MTO），可空
 */
public record MrpSuggestion(Long suggestionId, String suggestionNo, Long materialId, BigDecimal qty, LocalDate planStart, LocalDate planEnd,
                            Long plannerId, Long deptId, Long salesOrderLineId, String remark) {
}
