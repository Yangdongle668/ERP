package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已下达生产订单对某子件的用料需求。openQty = 未领 = max(0, 应领 − 已领 + 已退良品)。
 */
public record ComponentDemandDTO(Long prodOrderId, String prodOrderNo, Long productId, Long componentId, BigDecimal requiredQty,
                                 BigDecimal issuedQty, BigDecimal openQty, LocalDate planStart, String prodStatus) {
}
