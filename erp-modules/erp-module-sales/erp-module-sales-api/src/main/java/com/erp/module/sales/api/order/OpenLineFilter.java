package com.erp.module.sales.api.order;

import java.time.LocalDate;

/**
 * 未出完订单行查询条件，字段均可为空。
 *
 * @param dueTo 交期（承诺交期，无则要求交期）不晚于该日期
 */
public record OpenLineFilter(Long customerId, Long materialId, Long orderId, Long ownerId, LocalDate dueTo) {

    public static OpenLineFilter all() {
        return new OpenLineFilter(null, null, null, null, null);
    }
}
