package com.erp.module.shipping.api.query;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已出货的出货单行（仅“已出货 / 已完成”的出货单）。数量为基本单位，金额原币含税。
 *
 * @param shipmentStatus SHIPPED / COMPLETED
 */
public record ShippedLineDTO(Long shipmentId, String shipmentNo, Long shipmentLineId, LocalDate shipDate, String shipmentStatus, Long customerId,
                             Long orderId, String orderNo, Long orderLineId, Long materialId, String batchNo, BigDecimal baseQty, String currency,
                             BigDecimal priceInclTax, BigDecimal amount, String blNo, String transportMode) {
}
