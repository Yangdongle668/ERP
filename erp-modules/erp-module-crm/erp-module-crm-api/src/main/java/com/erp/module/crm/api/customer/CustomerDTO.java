package com.erp.module.crm.api.customer;

import java.math.BigDecimal;

/**
 * 客户（需求 03-01 crm_customer）。
 *
 * @param salesTaxRate 默认销项税率（小数，0.13 = 13%）
 * @param creditLimit  信用额度（本位币），为空表示不控制额度
 */
public record CustomerDTO(
        Long id,
        String code,
        String name,
        String nameEn,
        String shortName,
        String customerType,
        String level,
        String country,
        boolean foreign,
        String currency,
        Long paymentTermId,
        String tradeTerm,
        BigDecimal salesTaxRate,
        String taxNo,
        Long ownerId,
        Long deptId,
        BigDecimal creditLimit,
        Integer creditDays,
        CustomerStatus status) {
}
