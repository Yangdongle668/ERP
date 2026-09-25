package com.erp.module.sales.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单行对外视图（出货、PMC、财务使用）。数量为基本单位。
 *
 * @param priceInclTax   含税单价（每销售单位，原币）
 * @param basePriceInclTax 含税单价折算到每基本单位（原币），出货、退货按此计算金额
 * @param openQty        未出货数量 = baseQty − shippedQty（不小于 0）
 * @param noticeableQty  可通知数量 = baseQty × (1 + 超出货比例) − noticedQty（不小于 0）
 * @param lineStatus     OPEN 未完成 / SHIPPED 已出齐 / CLOSED 已关闭
 * @param orderStatus    订单通用状态（DocStatus 名称）
 */
public record SalesOrderLineDTO(Long lineId, Long orderId, String orderNo, int lineNo, String orderType, String orderStatus,
                                Long customerId, Long ownerId, Long deptId, String currency, Long materialId, String customerPartNo,
                                String description, String uom, BigDecimal qty, BigDecimal baseQty, BigDecimal priceInclTax,
                                BigDecimal basePriceInclTax, BigDecimal taxRate, LocalDate requiredDate, LocalDate promisedDate,
                                BigDecimal noticedQty, BigDecimal shippedQty, BigDecimal returnedQty, BigDecimal invoicedQty,
                                BigDecimal openQty, BigDecimal noticeableQty, String lineStatus) {

    /** 交期：承诺交期优先，没有时取要求交期 */
    public LocalDate dueDate() {
        return promisedDate != null ? promisedDate : requiredDate;
    }
}
