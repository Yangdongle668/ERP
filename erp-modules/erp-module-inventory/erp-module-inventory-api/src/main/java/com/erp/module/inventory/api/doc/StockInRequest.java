package com.erp.module.inventory.api.doc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生成入库单请求。warehouseId 为空时由仓库模块按规则确定（需检 → 待检仓；免检 → 物料默认仓），
 * 行上的物料可能因此被拆分到多张入库单。
 */
public record StockInRequest(
        StockInType inType,
        SourceRef source,
        Long warehouseId,
        LocalDate docDate,
        Long supplierId,
        Long customerId,
        List<Line> lines) {

    /**
     * @param qty      业务单位数量
     * @param unitCost 入库单价（本位币、不含税、基本单位），可空
     */
    public record Line(
            Long sourceLineId,
            Long materialId,
            String uom,
            BigDecimal qty,
            String batchNo,
            String supplierBatchNo,
            LocalDate productionDate,
            BigDecimal unitCost,
            List<String> serialNos) {
    }
}
