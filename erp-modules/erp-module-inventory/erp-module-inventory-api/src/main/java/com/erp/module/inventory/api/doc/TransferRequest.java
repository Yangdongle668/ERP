package com.erp.module.inventory.api.doc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生成调拨单请求（品质检验判定后调用）。检验调拨时 toWarehouseId 可为空：
 * 合格/特采调入物料默认仓，不合格调入不良品仓，按判定结果拆成两张单。
 *
 * @param inspectionId 检验单 ID
 */
public record TransferRequest(TransferType transferType, SourceRef source, Long inspectionId, Long fromWarehouseId, Long toWarehouseId,
                              LocalDate docDate, String reason, List<Line> lines) {

    /** @param qty 基本单位数量 */
    public record Line(Long sourceLineId, Long materialId, String batchNo, Long fromLocationId, BigDecimal qty, JudgeResult judgeResult,
                       List<String> serialNos) {
    }
}
