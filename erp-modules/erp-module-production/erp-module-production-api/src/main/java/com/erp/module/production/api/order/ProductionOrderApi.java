package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 生产订单写入（PMC 转单、研发工程样品） */
public interface ProductionOrderApi {

    /** MRP 建议转生产订单：直接为“已计划”，返回订单 ID（与入参顺序一致） */
    List<Long> createFromMrp(List<MrpSuggestion> suggestions);

    /**
     * 下达已计划的订单（PMC“转生产订单并下达”）：缺料时照常下达，返回缺料等提示。
     */
    List<String> release(Long prodOrderId);

    /**
     * 修改计划开工 / 完工（PMC 排产回写）：只对未完工订单，记录操作日志。
     */
    void updatePlanDates(Long prodOrderId, LocalDate planStart, LocalDate planEnd, String reason);

    /** 样品生产订单（类型 SAMPLE，已计划）；完工后发布 {@link ProductionOrderCompletedEvent} 并回调样品单 */
    Long createSampleOrder(Long sampleId, String sampleNo, Long materialId, BigDecimal qty, LocalDate requiredDate);
}
