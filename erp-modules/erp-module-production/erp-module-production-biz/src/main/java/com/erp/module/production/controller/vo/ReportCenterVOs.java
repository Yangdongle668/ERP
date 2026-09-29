package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 生产报表（需求 09-08） */
public final class ReportCenterVOs {

    private ReportCenterVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ProgressQuery extends PageParam {
        private Long deptId;
        private Long materialId;
        private LocalDate planEndFrom;
        private LocalDate planEndTo;
        private String columns;
    }

    public record OpProgress(int seq, String operation, BigDecimal goodQty, boolean done) {
    }

    /** @param delayed 预计延期：计划完工 < 今天且未完工 */
    public record ProgressRow(Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName, BigDecimal qty,
                              List<OpProgress> operations, BigDecimal completedQty, BigDecimal stockedQty, LocalDate planEnd, boolean delayed,
                              String prodStatus, String salesOrderNo, LocalDate customerDueDate, String deptName) {
    }

    @Data
    public static class VarianceQuery {
        private Long prodOrderId;
        private LocalDate closedFrom;
        private LocalDate closedTo;
        private Long materialId;
        private Long deptId;
    }

    /**
     * @param theoreticalQty 理论用量 = (合格 + 报废) × 单位用量 × (1 + 损耗)，按单位精度向上取整
     * @param netQty         实际净耗用 = 已领 − 已退
     * @param varianceRate   差异率 = 差异 ÷ 理论
     */
    public record VarianceRow(Long prodOrderId, String prodOrderNo, String productCode, Long componentId, String componentCode, String componentName,
                              String uom, BigDecimal theoreticalQty, BigDecimal netQty, BigDecimal varianceQty, BigDecimal varianceRate,
                              BigDecimal overIssuedQty, String overReasons) {
    }

    @Data
    public static class OutputQuery {
        private LocalDate from;
        private LocalDate to;
        private Long deptId;
        /** DATE / DEPT / WORK_CENTER / SHIFT / OPERATOR / PRODUCT */
        private String groupBy;
    }

    /** @param stdHours 标准工时 = 合格 × 标准秒 ÷ 3600；efficiency = 标准 ÷ 实际；perCapita 人均产量 = 合格 ÷ 人数 */
    public record OutputRow(String key, String label, BigDecimal goodQty, BigDecimal scrapQty, BigDecimal workHours, BigDecimal stdHours,
                            BigDecimal efficiency, int headcount, BigDecimal perCapita) {
    }

    @Data
    public static class AchievementQuery {
        private LocalDate from;
        private LocalDate to;
        private Long deptId;
    }

    public record DelayedOrder(Long prodOrderId, String prodOrderNo, String materialCode, String materialName, LocalDate planEnd, LocalDate actualEnd,
                               int delayDays, String prodStatus) {
    }

    /** 计划达成率 = 按期完工数 ÷ 应完工数（期间内计划完工的已下达订单） */
    public record Achievement(int dueCount, int onTimeCount, BigDecimal rate, List<DelayedOrder> delayed) {
    }
}
