package com.erp.module.inventory.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 库存查询与报表（需求 08-08） */
public final class ReportVOs {

    private ReportVOs() {
    }

    // ==================== 库存查询 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class StockQuery extends PageParam {
        /** 物料编码前缀 / 名称规格模糊 */
        private String keyword;
        private Long materialId;
        private Long categoryId;
        /** 仓库类型，逗号分隔 */
        private String warehouseTypes;
        /** 仓库，逗号分隔 */
        private String warehouseIds;
        private String batchNo;
        private Long locationId;
        private Boolean showZero;
        /** MATERIAL / WAREHOUSE / BATCH */
        private String groupBy;
    }

    /** 金额类字段无 inv:stock:cost 权限时为空 */
    public record StockRow(String key, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                           String categoryName, Long warehouseId, String warehouseName, WarehouseType warehouseType, Long locationId,
                           String locationCode, String batchNo, LocalDate productionDate, LocalDate expireDate, boolean concession, boolean frozen,
                           BigDecimal onHandQty, BigDecimal availableQty, BigDecimal reservedQty, BigDecimal qcQty, BigDecimal ngQty,
                           BigDecimal safetyStock, boolean belowSafety, BigDecimal refCost, BigDecimal amount, LocalDate lastInDate,
                           LocalDate lastOutDate) {
    }

    /** totalQty 仅在单一单位时有值 */
    public record StockPage(List<StockRow> list, long total, BigDecimal totalQty, BigDecimal totalAmount) {
    }

    // ==================== 库存流水 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TxnQuery extends PageParam {
        private String keyword;
        private Long materialId;
        private Long warehouseId;
        private String batchNo;
        /** IN / OUT */
        private String direction;
        /** 出入库类型，逗号分隔 */
        private String bizTypes;
        /** 单号或来源单号 */
        private String docNo;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record TxnRow(Long id, LocalDate bizDate, String docType, Long docId, String docNo, String bizType, String sourceType, Long sourceId,
                         String sourceNo, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                         String warehouseName, String locationCode, String batchNo, BigDecimal inQty, BigDecimal outQty, BigDecimal balanceQty,
                         BigDecimal unitCost, BigDecimal amount, boolean reversal, String operatorName, LocalDateTime createdAt) {
    }

    // ==================== 收发存 ====================

    @Data
    public static class SummaryQuery {
        /** yyyyMM，默认本月 */
        private String periodFrom;
        private String periodTo;
        private Long warehouseId;
        private Long categoryId;
        private String keyword;
        /** MATERIAL / WAREHOUSE */
        private String level;
        private Boolean showIdle;
    }

    /**
     * inDetail / outDetail：按类型细分的数量（键见 SummaryResult.inKeys/outKeys）；
     * 金额为空表示未计算（本期出库成本在月末成本计算后显示）。
     */
    public record SummaryRow(Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, Long warehouseId,
                             String warehouseName, BigDecimal openingQty, BigDecimal openingAmount, BigDecimal inQty, Map<String, BigDecimal> inDetail,
                             BigDecimal inAmount, BigDecimal outQty, Map<String, BigDecimal> outDetail, BigDecimal outAmount, BigDecimal closingQty,
                             BigDecimal closingAmount, boolean mismatch) {
    }

    public record SummaryResult(String periodFrom, String periodTo, boolean costCalculated, List<Bucket> inKeys, List<Bucket> outKeys,
                                List<SummaryRow> rows) {
    }

    public record Bucket(String key, String label) {
    }

    // ==================== 库龄、呆滞、预警 ====================

    @Data
    public static class AgingQuery {
        private LocalDate asOf;
        private String warehouseTypes;
        private Long categoryId;
        /** 区间上限天数，逗号分隔，默认 30,90,180,365 */
        private String buckets;
    }

    public record AgingRow(Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal qty,
                           List<BigDecimal> bucketQty, List<BigDecimal> bucketAmount, int maxDays) {
    }

    public record AgingResult(List<String> bucketLabels, List<AgingRow> rows, List<BigDecimal> bucketTotals) {
    }

    @Data
    public static class SlowQuery {
        private Integer days;
        private String warehouseTypes;
        private Long categoryId;
    }

    public record SlowRow(Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal qty,
                          BigDecimal amount, LocalDate lastInDate, LocalDate lastOutDate, int idleDays, boolean bomUsed) {
    }

    /** type = LOW / HIGH / EXPIRY / QC_OVERDUE */
    public record AlertRow(String type, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                           Long warehouseId, String warehouseName, String batchNo, BigDecimal qty, BigDecimal safetyStock, BigDecimal maxStock,
                           BigDecimal availableQty, BigDecimal inTransitQty, BigDecimal gap, LocalDate expireDate, Integer daysLeft,
                           LocalDateTime inAt, Long waitHours, String sourceNo, String buyerName) {
    }

    public record AlertCounts(long low, long high, long expiry, long qcOverdue) {
    }
}
