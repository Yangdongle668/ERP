package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;

/**
 * 库存过账引擎（需求 08-仓库/02-库存模型与过账）。
 *
 * <p>只由仓库模块自己的入库单、出库单、调拨单、盘点单在“确认”时调用。
 * 其他业务模块<b>不要</b>直接调用本接口，而应通过 {@link com.erp.module.inventory.api.doc.InventoryDocApi}
 * 生成仓库单据，由仓管员确认后过账。
 * 可用量查询请使用 {@link InventoryQueryApi}。
 */
public interface InventoryApi {

    /** 过账（入库或出库）。 */
    void post(StockPostingRequest request);

    /** 冲销某张单据此前的过账（反审核时调用），生成反向流水。 */
    void reverse(String bizType, Long bizId);

    /**
     * 可用量：只统计可用仓（{@link com.erp.module.inventory.api.warehouse.WarehouseType#available()}）合格库存减去预留。
     *
     * @deprecated 使用 {@link InventoryQueryApi#getAvailableQty(Long)}
     */
    @Deprecated
    BigDecimal getAvailableQty(Long materialId);
}
