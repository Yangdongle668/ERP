package com.erp.module.sales.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 事件中的订单行快照（基本单位） */
public record SalesOrderLineInfo(Long lineId, int lineNo, Long materialId, BigDecimal baseQty, BigDecimal shippedQty,
                                 LocalDate requiredDate, LocalDate promisedDate, String lineStatus) {
}
