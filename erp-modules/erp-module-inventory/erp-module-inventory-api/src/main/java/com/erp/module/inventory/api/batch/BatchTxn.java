package com.erp.module.inventory.api.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 批次追溯的一条流水 */
public record BatchTxn(Long txnId, LocalDate bizDate, String direction, String bizType, String docNo, String sourceType, String sourceNo,
                       Long warehouseId, BigDecimal qty) {
}
