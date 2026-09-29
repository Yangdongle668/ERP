package com.erp.module.production.api.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 未完工生产订单（PMC 的 MRP、缺料分析、排产、交期预警使用）。数量为基本单位。
 *
 * @param remainingQty 剩余数量 = 计划 − 合格入库 − 报废（不小于 0）
 * @param materials    用料（已下达后固化；已计划订单为空，调用方按 BOM 展开）
 * @param operations   工序（已下达后固化；已计划订单为空，调用方按工艺路线）
 */
public record OpenOrderDTO(Long id, String docNo, String orderType, String prodStatus, Long materialId, BigDecimal qty, BigDecimal completedQty,
                           BigDecimal scrappedQty, BigDecimal qualifiedStockedQty, BigDecimal remainingQty, Long bomId, Long routingId,
                           LocalDate planStart, LocalDate planEnd, LocalDateTime releasedAt, int priority, Long deptId, Long salesOrderLineId,
                           String salesOrderNo, List<Material> materials, List<Operation> operations) {

    /** @param openQty 未领 = max(0, 应领 − 已领 + 已退良品)（倒冲物料同口径：应领 − 已倒冲） */
    public record Material(Long lineId, Long componentId, BigDecimal qtyPer, BigDecimal scrapRate, BigDecimal requiredQty, BigDecimal issuedQty,
                           BigDecimal returnedGoodQty, BigDecimal openQty, String issueMethod, Integer operationSeq) {
    }

    /** @param doneQty 已完成 = 合格 + 报废 */
    public record Operation(int seq, String operation, Long workCenterId, BigDecimal stdSetupMinutes, BigDecimal stdRunSeconds, boolean reportPoint,
                            BigDecimal doneQty) {
    }
}
