package com.erp.module.inventory.api.doc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 生成出库单请求。批次为空时由仓管员确认时分配（或自动分配）。 */
public record StockOutRequest(
        StockOutType outType,
        SourceRef source,
        Long warehouseId,
        LocalDate docDate,
        Long receiverDeptId,
        Long receiverId,
        Long supplierId,
        Long customerId,
        List<Line> lines) {

    public record Line(
            Long sourceLineId,
            Long materialId,
            String uom,
            BigDecimal qty,
            String batchNo,
            List<String> serialNos) {
    }
}
