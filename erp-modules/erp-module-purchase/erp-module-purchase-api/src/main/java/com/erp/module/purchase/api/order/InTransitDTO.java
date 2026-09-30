package com.erp.module.purchase.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 物料在途采购数量（基本单位）：已审核、执行中订单未关闭行的未到货数量（含委外单未收货数量）。
 *
 * @param details 按订单行的明细
 */
public record InTransitDTO(Long materialId, BigDecimal qty, List<Detail> details) {

    /**
     * @param expectedDate 预计到货日期：确认交期，没有时取要求日期
     * @param requiredDate 要求日期（采购订单行 / 委外单的需求日期）
     */
    public record Detail(String docType, Long docId, String docNo, Long lineId, Long supplierId, BigDecimal qty, LocalDate expectedDate,
                         LocalDate requiredDate) {

        /** 兼容旧构造（要求日期取预计到货日期） */
        public Detail(String docType, Long docId, String docNo, Long lineId, Long supplierId, BigDecimal qty, LocalDate expectedDate) {
            this(docType, docId, docNo, lineId, supplierId, qty, expectedDate, expectedDate);
        }

        /** 按依据取到货日期：REQUIRED 取要求日期，其他（CONFIRMED）取确认交期优先 */
        public LocalDate dateBy(String basis) {
            if ("REQUIRED".equals(basis) && requiredDate != null) return requiredDate;
            return expectedDate != null ? expectedDate : requiredDate;
        }
    }
}
