package com.erp.module.inventory.api.doc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生成出库单请求。批次为空时由仓管员确认时分配（或自动分配）。
 *
 * @param reason 其他出库原因（字典 inv_other_out_reason，可空）：SCRAP 报废、DOWNGRADE 降级转换可从不良品仓出库
 * @param remark 单据备注（报废出库需要备注或附件说明）
 */
public record StockOutRequest(
        StockOutType outType,
        SourceRef source,
        Long warehouseId,
        LocalDate docDate,
        Long receiverDeptId,
        Long receiverId,
        Long supplierId,
        Long customerId,
        List<Line> lines,
        String reason,
        String remark) {

    public StockOutRequest(StockOutType outType, SourceRef source, Long warehouseId, LocalDate docDate, Long receiverDeptId, Long receiverId,
                           Long supplierId, Long customerId, List<Line> lines) {
        this(outType, source, warehouseId, docDate, receiverDeptId, receiverId, supplierId, customerId, lines, null, null);
    }

    public record Line(
            Long sourceLineId,
            Long materialId,
            String uom,
            BigDecimal qty,
            String batchNo,
            List<String> serialNos) {
    }
}
