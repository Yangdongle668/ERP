package com.erp.module.sales.api.price;

import java.math.BigDecimal;

/**
 * 取价结果（需求 04-01 第 3 节）。
 *
 * @param price        单价（每 uom，按 taxIncluded 为含税或不含税，原币）
 * @param sourceType   PRICE_LIST_CUSTOMER / PRICE_LIST_LEVEL / PRICE_LIST_ALL / QUOTATION / LAST_ORDER
 * @param sourceLabel  来源说明，如“客户价格表 PL-2026-001”
 */
public record SalesPriceDTO(BigDecimal price, boolean taxIncluded, String uom, String sourceType, Long sourceId, String sourceNo,
                            String sourceLabel) {
}
