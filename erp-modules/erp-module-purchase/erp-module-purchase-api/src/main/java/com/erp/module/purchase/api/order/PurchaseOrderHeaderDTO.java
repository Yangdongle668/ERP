package com.erp.module.purchase.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单头（财务预付款申请：预付累计不超过订单价税合计）。
 *
 * @param status DocStatus 名称
 */
public record PurchaseOrderHeaderDTO(Long id, String docNo, LocalDate docDate, Long supplierId, String currency, BigDecimal totalAmount,
                                     Long paymentTermId, String status) {
}
