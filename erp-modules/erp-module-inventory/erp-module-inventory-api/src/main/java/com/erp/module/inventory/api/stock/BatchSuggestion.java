package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 出库批次推荐（FIFO/FEFO）的一行；batchNo 为空表示不管理批次 */
public record BatchSuggestion(String batchNo, Long locationId, BigDecimal qty, LocalDate productionDate, LocalDate expireDate) {
}
