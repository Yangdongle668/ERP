package com.erp.module.production.api.trace;

import java.math.BigDecimal;

/**
 * 追溯关系的一行（已按 生产订单 + 投入物料 + 投入批次 汇总：领料为正、退料为负）。
 *
 * @param level 层级：1 为直接关系；反向追溯时半成品批次继续展开为 2、3…；正向追溯时产品批次继续向上为 2、3…
 */
public record TraceNode(int level, Long prodOrderId, String prodOrderNo, Long productMaterialId, String productBatchNo, Long componentMaterialId,
                        String componentBatchNo, BigDecimal qty) {
}
