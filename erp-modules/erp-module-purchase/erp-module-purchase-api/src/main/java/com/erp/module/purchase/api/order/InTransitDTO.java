package com.erp.module.purchase.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 物料在途采购数量（基本单位）：已审核、执行中订单未关闭行的未到货数量（含委外单未收货数量）。
 *
 * @param details 按订单行的明细（预计到货日期 = 确认交期，没有时取要求日期）
 */
public record InTransitDTO(Long materialId, BigDecimal qty, List<Detail> details) {

    public record Detail(String docType, Long docId, String docNo, Long lineId, Long supplierId, BigDecimal qty, LocalDate expectedDate) {
    }
}
