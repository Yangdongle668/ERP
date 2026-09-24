package com.erp.module.inventory.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 批次与序列号（需求 08-07） */
public final class BatchVOs {

    private BatchVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BatchQuery extends PageParam {
        private Long materialId;
        private String batchNo;
        private String supplierBatchNo;
        private Long supplierId;
        /** EXPIRED 已过期 / SOON 临期（参数天数内）/ NORMAL 正常 */
        private String expiry;
        private Boolean frozen;
        /** 只看有库存（默认是） */
        private Boolean hasStock;
    }

    public record BatchRow(Long id, Long materialId, String materialCode, String materialName, String materialSpec, String uom, String batchNo,
                           String supplierBatchNo, Long supplierId, LocalDate productionDate, LocalDate expireDate, Long remainingDays,
                           LocalDate firstInDate, BigDecimal onHandQty, BigDecimal availableQty, boolean concession, boolean frozen,
                           String frozenReason, String frozenByModule, String sourceType, String sourceNo, String remark, int version) {
    }

    /** BatchSelect：本仓库某物料的可用批次 */
    public record AvailableBatch(String batchNo, Long locationId, String locationCode, BigDecimal availableQty, LocalDate productionDate,
                                 LocalDate expiryDate, LocalDate inDate, boolean frozen, boolean expired) {
    }

    public record Distribution(Long warehouseId, String warehouseName, Long locationId, String locationCode, BigDecimal qty) {
    }

    public record TxnRow(Long id, LocalDate bizDate, String direction, String bizType, String docType, Long docId, String docNo, String sourceType,
                         String sourceNo, Long warehouseId, String warehouseName, BigDecimal qty, BigDecimal balanceQty, boolean reversal,
                         LocalDateTime createdAt) {
    }

    public record BatchDetail(BatchRow batch, List<Distribution> distribution, List<TxnRow> txns) {
    }

    public record ReasonReq(@Size(max = 256) String reason) {
    }

    public record BatchUpdate(@Size(max = 64) String supplierBatchNo, LocalDate productionDate, LocalDate expireDate,
                              @Size(max = 256) String remark, @Size(max = 256) String reason, Integer version) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SerialQuery extends PageParam {
        private Long materialId;
        private String serialNo;
        private String status;
        private Long customerId;
    }

    public record SerialRow(Long id, Long materialId, String materialCode, String materialName, String serialNo, String batchNo, String status,
                            Long warehouseId, String warehouseName, Long locationId, String locationCode, Long customerId, String lastDocNo) {
    }

    public record SerialHistory(Long txnId, String direction, String docNo, LocalDateTime createdAt) {
    }
}
