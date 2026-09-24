package com.erp.module.engineering.api.material;

import java.math.BigDecimal;

/**
 * 其他模块对某物料的引用情况（{@link MaterialReferenceChecker} 返回）。
 *
 * @param stockQty      现存量（基本单位），没有库存数据的模块返回 0
 * @param openDocCount  未完成单据张数（采购、销售、生产、出入库等）
 * @param used          是否曾被任何单据引用（ENG-MAT-R09：被引用过的草稿物料也不能删除）
 * @param trackingLocked 有库存或未完成出入库单据，不能修改库存管理方式（ENG-MAT-R10）
 */
public record MaterialUsage(BigDecimal stockQty, int openDocCount, boolean used, boolean trackingLocked) {

    public static final MaterialUsage NONE = new MaterialUsage(BigDecimal.ZERO, 0, false, false);

    public MaterialUsage plus(MaterialUsage o) {
        return new MaterialUsage(stockQty.add(o.stockQty), openDocCount + o.openDocCount, used || o.used, trackingLocked || o.trackingLocked);
    }
}
