package com.erp.module.sales.controller.vo;

import com.erp.common.result.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售报表（需求 04-08），金额均为本位币 */
public final class ReportVOs {

    private ReportVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OpenOrderQuery extends PageParam {
        private Long customerId;
        private Long ownerId;
        private Long materialId;
        private String orderNo;
        /** 距交期 ≤ N 天（含已过期） */
        private Integer dueWithinDays;
        private String columns;
    }

    /**
     * @param daysToDue 距交期天数（负数为已过期）
     * @param wipQty    在制数量（生产模块提供后显示，当前为空）
     */
    public record OpenOrderRow(Long orderId, String orderNo, Long orderLineId, int lineNo, Long customerId, String customerName, Long materialId,
                               String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal orderQty, BigDecimal shippedQty,
                               BigDecimal openQty, BigDecimal availableQty, BigDecimal wipQty, LocalDate requiredDate, LocalDate promisedDate,
                               int daysToDue, String ownerName) {
    }

    /** 执行跟踪时间线的一步 */
    public record TraceStep(String step, String label, LocalDate date, LocalDateTime time, String docNo, BigDecimal qty, BigDecimal amount,
                            String remark) {
    }

    public record OrderTrace(Long orderId, String orderNo, Long orderLineId, int lineNo, String materialCode, String materialName, BigDecimal qty,
                             List<TraceStep> steps) {
    }

    @Data
    public static class PeriodQuery {
        private LocalDate from;
        private LocalDate to;
        private Long ownerId;
        private Long deptId;
        private Long customerId;
        /** 报价成功率分组：OWNER / CUSTOMER / CATEGORY */
        private String groupBy;
    }

    public record PerformanceRow(Long ownerId, String ownerName, String deptName, BigDecimal orderAmount, BigDecimal shipAmount,
                                 BigDecimal receiptAmount, BigDecimal marginAmount, int newCustomers, int orderCount) {
    }

    public record TrendPoint(String month, BigDecimal orderAmount, BigDecimal shipAmount, BigDecimal receiptAmount) {
    }

    public record Performance(String baseCurrency, List<PerformanceRow> rows, List<TrendPoint> trend) {
    }

    public record QuoteSuccessRow(String groupKey, String groupName, int quoteCount, int wonCount, int lostCount, BigDecimal successRate,
                                  BigDecimal avgCycleDays) {
    }

    public record NameValue(String name, String label, BigDecimal value) {
    }

    public record QuoteSuccess(int quoteCount, int wonCount, BigDecimal successRate, List<QuoteSuccessRow> rows, List<NameValue> lostReasons) {
    }

    /** @param abcClass 累计占比 ≤ 80% 为 A、≤ 95% 为 B、其余 C */
    public record CustomerRankRow(int rank, Long customerId, String customerCode, String customerName, BigDecimal orderAmount, BigDecimal shipAmount,
                                  BigDecimal share, BigDecimal lastYearAmount, BigDecimal growth, String abcClass) {
    }
}
