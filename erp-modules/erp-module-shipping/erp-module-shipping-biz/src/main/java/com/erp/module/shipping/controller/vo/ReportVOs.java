package com.erp.module.shipping.controller.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 出货报表（11-06） */
public final class ReportVOs {

    private ReportVOs() {
    }

    @Data
    public static class ReportQuery {
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Long customerId;
        private Long materialId;
        private Long ownerId;
        /** 待出货：承诺交期在未来 N 天内（默认参数 shp.report.pending-days） */
        private Integer days;
        /** 汇总维度：CUSTOMER / MATERIAL / MONTH / OWNER */
        private String groupBy;
    }

    /** 待出货清单：已审核未出货的通知行 + 未通知的订单行（noticeId 为空显示“未通知”） */
    public record PendingRow(Long customerId, String customerName, Long orderId, String orderNo, Integer lineNo, Long materialId, String materialCode,
                             String materialName, BigDecimal qty, LocalDate dueDate, boolean overdue, Long noticeId, String noticeNo, String noticeStatus,
                             BigDecimal availableQty, Long ownerId, String ownerName) {
    }

    public record DetailRow(Long shipmentId, String shipmentNo, LocalDate shipDate, Long customerId, String customerName, Long orderId, String orderNo,
                            String customerPoNo, Long materialId, String materialCode, String materialName, String batchNo, BigDecimal qty, String uom,
                            String currency, BigDecimal price, BigDecimal amount, BigDecimal amountBase, String transportMode, String blNo, Long ownerId,
                            String ownerName) {
    }

    public record SummaryRow(String key, String label, int lines, BigDecimal qty, BigDecimal amountBase) {
    }

    public record DetailReport(List<DetailRow> rows, List<SummaryRow> summary, BigDecimal totalQty, BigDecimal totalAmountBase) {
    }

    public record OnTimeGroup(String key, String label, int total, int onTime, BigDecimal rate) {
    }

    public record DelayRow(Long orderId, String orderNo, Integer lineNo, Long customerId, String customerName, Long materialId, String materialCode,
                           String materialName, LocalDate dueDate, LocalDate firstShipDate, long delayDays, String ownerName) {
    }

    public record OnTimeReport(int total, int onTime, BigDecimal rate, List<OnTimeGroup> byCustomer, List<OnTimeGroup> byOwner,
                               List<OnTimeGroup> byMaterial, List<DelayRow> delays) {
    }

    public record ExportStatRow(String month, String country, String hsCode, int shipments, BigDecimal qty, BigDecimal amountBase) {
    }
}
