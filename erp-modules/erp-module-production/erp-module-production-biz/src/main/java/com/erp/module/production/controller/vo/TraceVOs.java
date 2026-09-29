package com.erp.module.production.controller.vo;

import java.math.BigDecimal;
import java.util.List;

/** 生产追溯（需求 09-07） */
public final class TraceVOs {

    private TraceVOs() {
    }

    /**
     * 追溯树节点。反向：子节点为投入物料批次（半成品批次继续展开）；正向：子节点为使用该批次的产品批次（继续向上）。
     *
     * @param batchTracked 物料是否启用批次管理（未启用时只显示数量，不能继续正向追溯）
     * @param supplierId   原材料批次的供应商（来自仓库批次档案）
     */
    public record TraceTreeNode(String key, int level, Long materialId, String materialCode, String materialName, String batchNo, boolean batchTracked,
                                BigDecimal qty, Long prodOrderId, String prodOrderNo, BigDecimal completedQty, Long supplierId, String supplierBatchNo,
                                String batchSourceNo, boolean concession, List<TraceTreeNode> children) {
    }

    /** 顶部汇总：涉及生产订单数、产品批次数、原材料批次数 */
    public record TraceResult(String direction, TraceTreeNode root, int orderCount, int batchCount, List<String> notes) {
    }

    /** 导出（召回清单）的扁平行 */
    public record TraceRow(int level, String prodOrderNo, String productCode, String productName, String productBatchNo, String componentCode,
                           String componentName, String componentBatchNo, BigDecimal qty, Long supplierId) {
    }
}
