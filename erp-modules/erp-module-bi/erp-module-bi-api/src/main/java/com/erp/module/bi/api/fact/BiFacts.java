package com.erp.module.bi.api.fact;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BI 事实记录（需求 13-01 第 3 节）。由各业务模块的 {@link BiFactProvider} 从本模块业务表提取，金额均为本位币；
 * BI 按汇总表粒度合并（同一键的多条记录相加），不直接访问业务表。
 */
public final class BiFacts {

    private BiFacts() {
    }

    /**
     * 销售事实（日期 × 客户 × 物料 × 业务员）。
     *
     * @param orderAmount     审核的销售订单价税合计（订单审核日）
     * @param shipAmount      出货确认金额（不含税，出货日）
     * @param shipQty         出货数量（基本单位，用于按期间单位成本计算出货成本）
     * @param returnAmount    退货金额（不含税，退货收货日，冲减出货额）
     * @param shipLineCount   本日完成首次出货的订单行数
     * @param onTimeLineCount 其中首次出货日 ≤ 承诺交期（无则要求交期）的行数
     * @param receiptAmount   收款核销到应收 + 预收确认金额（核销日；物料为空）
     */
    public record SalesFact(LocalDate date, Long customerId, Long materialId, Long ownerId, Long deptId, BigDecimal orderAmount, BigDecimal shipAmount,
                            BigDecimal shipQty, BigDecimal returnAmount, int shipLineCount, int onTimeLineCount, BigDecimal receiptAmount) {
    }

    /**
     * 采购事实（日期 × 供应商 × 物料 × 采购员）。
     *
     * @param orderAmount     审核的采购订单价税合计（订单日期）
     * @param receiptAmount   合格入库金额（不含税，入库日）
     * @param dueLineCount    本日到期（确认交期，无则需求日期）的订单行数
     * @param onTimeLineCount 其中首次到货日 ≤ 到期日的行数
     */
    public record PurchaseFact(LocalDate date, Long supplierId, Long materialId, Long buyerId, Long deptId, BigDecimal orderAmount, BigDecimal orderQty,
                               BigDecimal receiptAmount, BigDecimal receiptQty, int dueLineCount, int onTimeLineCount) {
    }

    /** 当前库存（仓库 × 物料）：数量、参考金额（数量 × 参考单价）、最近出库日 */
    public record InventoryFact(Long warehouseId, String warehouseType, Long materialId, BigDecimal qty, BigDecimal amount, LocalDate lastOutDate,
                                LocalDate lastInDate) {
    }

    /** 库存流水（期间 yyyyMM × 仓库类型 × 物料）：入库、出库金额（周转天数） */
    public record InventoryFlowFact(String period, String warehouseType, Long materialId, BigDecimal inAmount, BigDecimal outAmount) {
    }

    /**
     * 生产事实（日期 × 车间 × 产品）。
     *
     * @param planQty       计划完工日为该日的生产订单数量
     * @param inQty         合格完工入库数量
     * @param firstPassQty  一次合格数量（报工合格且非返工）
     */
    public record ProductionFact(LocalDate date, Long deptId, Long materialId, BigDecimal planQty, BigDecimal goodQty, BigDecimal defectQty,
                                 BigDecimal scrapQty, BigDecimal workHours, BigDecimal stdHours, BigDecimal inQty, BigDecimal firstPassQty,
                                 int delayedOrderCount) {
    }

    /**
     * 品质事实（日期 × 检验类型 × 供应商 / 客户 × 物料）。检验类型：IQC / IPQC / FQC / OQC / RETURN，客诉、NCR 使用 COMPLAINT / NCR。
     */
    public record QualityFact(LocalDate date, String inspectType, Long supplierId, Long customerId, Long materialId, int lotCount, int passCount,
                              int concessionCount, int rejectCount, int defectCount, int ncrCount, int complaintCount) {
    }

    /** 财务事实（期间 yyyyMM × 往来单位）：partnerType CUSTOMER / SUPPLIER；begin / add / settle / end 为本位币 */
    public record FinanceFact(String period, String partnerType, Long partnerId, BigDecimal beginBalance, BigDecimal addAmount, BigDecimal settleAmount,
                              BigDecimal endBalance, BigDecimal overdueAmount) {
    }
}
