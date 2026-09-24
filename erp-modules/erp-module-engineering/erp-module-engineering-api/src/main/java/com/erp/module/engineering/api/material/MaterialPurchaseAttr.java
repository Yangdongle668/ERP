package com.erp.module.engineering.api.material;

import java.math.BigDecimal;

/** 采购属性（资材使用）。overReceivePct、purchaseTaxRate 为小数，如 0.05 表示 5%。 */
public record MaterialPurchaseAttr(Long materialId, Long buyerId, String purchaseUom, BigDecimal overReceivePct,
                                   int leadTimeDays, BigDecimal moq, BigDecimal mpq, BigDecimal purchaseTaxRate) {
}
