package com.erp.module.sales.api.forecast;

import java.math.BigDecimal;

/**
 * 净预测（冲销后）。period 为 yyyyMM；netQty = max(0, qty − consumedQty)，基本单位。
 */
public record NetForecastDTO(Long forecastId, String forecastNo, Long lineId, Long customerId, Long materialId, String period,
                             BigDecimal qty, BigDecimal consumedQty, BigDecimal netQty) {
}
