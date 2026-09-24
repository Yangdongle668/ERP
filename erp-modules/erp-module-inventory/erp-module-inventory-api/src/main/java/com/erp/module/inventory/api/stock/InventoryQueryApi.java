package com.erp.module.inventory.api.stock;

import com.erp.module.inventory.api.warehouse.WarehouseType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 库存查询（需求 08-仓库/02 第 3 节统一口径）。 */
public interface InventoryQueryApi {

    /** 全部可用仓的可用量（可用现存量 − 有效预留） */
    BigDecimal getAvailableQty(Long materialId);

    /** 指定仓库的可用量；不可用仓返回 0 */
    BigDecimal getAvailableQty(Long materialId, Long warehouseId);

    Map<Long, StockSummary> getStockSummary(Collection<Long> materialIds);

    /**
     * 按物料出库规则（FIFO：首次入库日期；FEFO：到期日，排除过期）推荐批次，数量不够时返回已有的全部批次。
     * 冻结、过期批次不参与推荐。
     */
    List<BatchSuggestion> suggestBatches(Long materialId, Long warehouseId, BigDecimal qty);

    /** 按仓库类型汇总的现存量 */
    Map<WarehouseType, BigDecimal> getOnHandByWarehouseType(Long materialId);
}
