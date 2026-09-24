package com.erp.module.inventory.api.doc;

import java.util.List;

/**
 * 业务模块生成仓库单据的入口（单据驱动库存，见需求 08-仓库/README 第 3 节）。
 *
 * <p>生成的单据为草稿，仓管员确认后过账，并发布 {@link StockInConfirmedEvent} / {@link StockOutConfirmedEvent}。
 * 必须在调用方事务内调用：业务单据审核失败时生成的仓库单据一并回滚。
 */
public interface InventoryDocApi {

    /** @return 生成的入库单 ID（可能按仓库拆分为多张） */
    List<Long> createStockIn(StockInRequest request);

    List<Long> createStockOut(StockOutRequest request);

    /**
     * 来源单据撤销（反审核）时调用：作废该来源生成的未确认仓库单据；
     * 已确认的单据存在时抛出 BizException，阻止来源撤销。
     */
    void cancelBySource(String sourceType, Long sourceId);
}
