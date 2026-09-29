package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生产订单进度 */
public record ProgressDTO(Long prodOrderId, String prodOrderNo, Long materialId, BigDecimal qty, BigDecimal completedQty, BigDecimal scrappedQty,
                          BigDecimal stockedQty, BigDecimal qualifiedStockedQty, String prodStatus, LocalDate planStart, LocalDate planEnd,
                          Long salesOrderLineId) {
}
