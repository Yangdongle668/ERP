package com.erp.module.quality.api.stats;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 质量统计（资材供应商评估使用） */
public interface QualityStatsApi {

    /** 供应商在期间内（按判定日期）的 IQC 批次统计与 SCAR 情况 */
    SupplierLotStats supplierLotStats(Long supplierId, LocalDate from, LocalDate to);

    /**
     * @param passRate      批次合格率（%，合格 + 挑选后良品 ÷ 检验批次；无批次为空）
     * @param scarIssued    期间发出的 SCAR 数
     * @param scarOverdue   期间内逾期未回复的 SCAR 数
     */
    record SupplierLotStats(Long supplierId, int lots, int qualifiedLots, int concessionLots, int rejectedLots, int sortedLots, BigDecimal passRate,
                            int scarIssued, int scarClosed, int scarOverdue) {
    }
}
