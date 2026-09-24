package com.erp.module.inventory.api.stock;

import java.math.BigDecimal;

/**
 * 库存记账唯一入口（需求文档 08 第 5.4 节）。其他模块不得直接修改库存表。
 *
 * <p>实现必须在调用方事务中执行（REQUIRED），失败抛出 BizException 使整个业务单据审核回滚。
 */
public interface InventoryApi {

    /** 过账（入库或出库）。 */
    void post(StockPostingRequest request);

    /** 冲销某张单据此前的过账（反审核时调用），生成反向流水。 */
    void reverse(String bizType, Long bizId);

    /** 可用量：只统计可用仓（{@link com.erp.module.inventory.api.warehouse.WarehouseType#available()}）合格库存减去预留。 */
    BigDecimal getAvailableQty(Long materialId);
}
