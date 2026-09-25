package com.erp.module.engineering.api.sample;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 样品生产订单请求（数量 = 样品数量，基本单位） */
public record SampleOrderRequest(Long sampleId, String sampleNo, Long materialId, BigDecimal qty, LocalDate requiredDate, Long projectId) {
}
