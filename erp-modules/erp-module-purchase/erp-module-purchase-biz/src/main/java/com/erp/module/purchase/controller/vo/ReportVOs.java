package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购报表（需求 07-11） */
public final class ReportVOs {

    private ReportVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TrackingQuery extends PageParam {
        private Long supplierId;
        private Long ownerId;
        private Long materialId;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        private LocalDate confirmedFrom;
        private LocalDate confirmedTo;
        /** 只看逾期（默认开） */
        private Boolean overdueOnly = Boolean.TRUE;
        /** 只看未回复交期 */
        private Boolean unconfirmedOnly;
        private String columns;
    }

    /** 数量为基本单位；overdueDays = 今天 − 确认交期（没有时要求日期），> 0 为逾期 */
    public record TrackingRow(Long orderLineId, Long orderId, String orderNo, Integer lineNo, Long supplierId, String supplierName, Long materialId,
                              String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal qty, BigDecimal receivedQty,
                              BigDecimal openQty, LocalDate requiredDate, LocalDate confirmedDate, long overdueDays, String demand, Long ownerId,
                              String ownerName, String lastFollowUp, LocalDateTime followUpAt) {
    }

    public record FollowUpReq(String content, LocalDate newDate) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ExecutionQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private Long ownerId;
        private Long materialId;
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String columns;
    }

    public record ExecutionRow(Long orderId, String orderNo, LocalDate docDate, Long supplierId, String supplierName, Integer lineNo, Long materialId,
                               String materialCode, String materialName, String baseUom, BigDecimal qty, BigDecimal receivedQty, BigDecimal stockedQty,
                               BigDecimal qualifiedQty, BigDecimal returnedQty, BigDecimal statementQty, BigDecimal openQty, String currency,
                               BigDecimal priceInclTax, BigDecimal totalAmount, String lineStatus) {
    }

    @Data
    public static class PriceTrendQuery {
        /** 物料（逗号分隔，最多 5 个） */
        private String materialIds;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    /** 月度实际采购单价（按到货合格数量加权，本位币不含税，每基本单位） */
    public record PriceTrendRow(String month, Long materialId, String materialCode, String materialName, Long supplierId, String supplierName,
                                BigDecimal qty, BigDecimal avgPrice, BigDecimal maxPrice, BigDecimal minPrice) {
    }

    public record TrendPoint(String month, BigDecimal avgPrice) {
    }

    public record TrendSeries(Long materialId, String materialCode, String materialName, List<TrendPoint> points) {
    }

    public record PriceTrend(List<String> months, List<TrendSeries> series, List<PriceTrendRow> rows) {
    }

    @Data
    public static class SummaryQuery {
        /** SUPPLIER / CATEGORY / MATERIAL / BUYER / MONTH */
        private String dim1 = "SUPPLIER";
        private String dim2;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    /** 金额为本位币；比率为百分数（2 位小数） */
    public record SummaryRow(String key1, String label1, String key2, String label2, BigDecimal orderAmount, BigDecimal receivedAmount,
                             BigDecimal qualifiedAmount, BigDecimal returnAmount, int orderLineCount, BigDecimal ontimeRate, BigDecimal lotPassRate) {
    }
}
