package com.erp.module.pmc.api.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 出货计划行（数量为基本单位） */
public record ShippingPlanLineDTO(Long id, Long planId, String planNo, String planWeek, Long orderLineId, Long customerId, Long materialId,
                                  BigDecimal planQty, BigDecimal noticedQty, LocalDate planShipDate, String transportMode, String lineStatus,
                                  String remark) {
}
