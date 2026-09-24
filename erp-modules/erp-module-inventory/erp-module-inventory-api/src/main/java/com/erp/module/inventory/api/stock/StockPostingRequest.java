package com.erp.module.inventory.api.stock;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存过账请求：一张业务单据的一次出库或入库。
 *
 * @param bizType  业务类型，如 PURCHASE_IN、PRODUCTION_ISSUE、SALES_OUT
 * @param bizId    来源单据 ID；同一 bizType + bizId + direction 重复过账会被拒绝（幂等）
 * @param bizDate  业务日期，决定落在哪个库存期间
 */
public record StockPostingRequest(
        String bizType,
        Long bizId,
        String bizNo,
        StockDirection direction,
        LocalDate bizDate,
        List<StockPostingLine> lines) {
}
