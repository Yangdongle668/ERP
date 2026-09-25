package com.erp.module.purchase.api.price;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购价格（每基本单位）。
 *
 * @param price        不含税单价
 * @param priceInclTax 含税单价
 * @param taxRate      税率（小数，0.13 表示 13%）
 * @param minQty       阶梯起始数量（基本单位）
 */
public record PurchasePriceDTO(Long id, Long supplierId, Long materialId, String currency, BigDecimal minQty, BigDecimal price,
                               BigDecimal taxRate, BigDecimal priceInclTax, LocalDate effectiveFrom, LocalDate effectiveTo) {
}
