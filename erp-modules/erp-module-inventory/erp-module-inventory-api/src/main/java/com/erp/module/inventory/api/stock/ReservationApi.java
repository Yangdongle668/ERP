package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;
import java.util.List;

/** 库存预留（需求 08-仓库/02 inv_reservation，P1）：销售订单、生产订单用料预留可用量。 */
public interface ReservationApi {

    /** warehouseId、batchNo 为空表示不限 */
    record Line(Long bizLineId, Long materialId, Long warehouseId, String batchNo, BigDecimal qty) {
    }

    /** 覆盖该业务单据此前的全部预留 */
    void reserve(String bizType, Long bizId, String bizNo, List<Line> lines);

    /** 释放该业务单据的全部预留 */
    void release(String bizType, Long bizId);
}
