package com.erp.module.inventory.api.cost;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成本核算用库存数据（财务 12-07）：期间流水、期初结存（按物料汇总，不分仓库）、回填出入库成本与期末结存金额。
 * 只对已月结的库存期间回填。
 */
public interface InventoryCostApi {

    /**
     * 期间的库存流水（不含调拨）
     *
     * @param direction  IN / OUT（冲销流水方向与原流水相反）
     * @param bizType    出入库类型：PURCHASE_IN / PRODUCTION_ISSUE / SALES_OUT …；盘点为 COUNT_GAIN / COUNT_LOSS
     * @param sourceType 来源单据类型（如 MFG_ISSUE / MFG_RETURN / MFG_FINISH）
     */
    record CostTxn(Long txnId, String docType, String bizType, String direction, boolean reversal, Long reversedTxnId, Long materialId,
                   Long warehouseId, BigDecimal qty, BigDecimal unitCost, BigDecimal amount, LocalDate bizDate, String sourceType, Long sourceId,
                   Long sourceLineId, String sourceNo, String docNo) {
    }

    /** 物料结存（全公司，不分仓库） */
    record MaterialBalance(Long materialId, BigDecimal qty, BigDecimal amount) {
    }

    /** 库存期间是否已月结；早于系统启用期间（库存未启用）视为已结账，其他不存在的期间返回 false */
    boolean isPeriodClosed(String period);

    List<CostTxn> getPeriodTxns(String period);

    /**
     * 期初结存：上期已回填成本的期末结存；上期未计算成本时按期间之前的全部流水（含期初导入）汇总，
     * 出库未计价的部分按入库平均单价估算
     */
    List<MaterialBalance> getOpeningBalances(String period);

    /** 回填流水单价与金额（只更新该期间的流水） */
    void applyCosts(String period, Map<Long, BigDecimal> unitCostByTxn);

    /** 回填期末结存：avg_cost = 物料加权单价，amount = 数量 × 单价 */
    void saveClosingCosts(String period, Map<Long, BigDecimal> unitCostByMaterial);
}
