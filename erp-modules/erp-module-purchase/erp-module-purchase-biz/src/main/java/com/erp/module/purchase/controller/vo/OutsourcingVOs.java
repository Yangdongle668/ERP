package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 委外加工（需求 07-07） */
public final class OutsourcingVOs {

    private OutsourcingVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OsQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private Long materialId;
        private String statuses;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
    }

    /** @param issuePct 发料进度（已发 ÷ 应发，所有用料合计） */
    public record OsRow(Long id, String docNo, LocalDate docDate, Long supplierId, String supplierName, Long materialId, String materialCode,
                        String materialName, String uom, BigDecimal qty, BigDecimal processPrice, String currency, LocalDate requiredDate,
                        BigDecimal issuePct, BigDecimal receivedQty, BigDecimal qualifiedQty, String status, String ownerName) {
    }

    /** 调整应发数量（需填写原因） */
    public record OsMaterialSave(@NotNull Long materialId, BigDecimal requiredQty, @Size(max = 256) String adjustReason) {
    }

    public record OsSave(@NotNull(message = "请选择加工商") Long supplierId, @NotNull(message = "请选择加工物料") Long materialId, Long bomId,
                         BigDecimal qty, BigDecimal processPrice, BigDecimal taxRate, String currency, BigDecimal exchangeRate,
                         LocalDate requiredDate, @Size(max = 1000) String remark, @Valid List<OsMaterialSave> materials, List<Long> fileIds,
                         Integer version) {
    }

    /** 用料（新建时预览、详情） */
    public record OsMaterialResp(Long id, Integer lineNo, Long materialId, String materialCode, String materialName, String materialSpec, String uom,
                                 BigDecimal qtyPer, BigDecimal requiredQty, BigDecimal issuedQty, BigDecimal returnedQty, BigDecimal consumedQty,
                                 BigDecimal lossQty, String lossReason, String adjustReason, BigDecimal availableQty, BigDecimal previewConsumed,
                                 BigDecimal previewLoss) {
    }

    public record BomPreview(Long bomId, String bomNo, Integer bomVersion, List<OsMaterialResp> materials) {
    }

    public record OsReceiptLine(Long receiptId, String receiptNo, LocalDateTime arrivalAt, String receiptStatus, BigDecimal qty, BigDecimal stockedQty,
                                String inspectStatus, BigDecimal qualifiedQty, BigDecimal rejectedQty) {
    }

    public record OsTxn(Long id, String txnType, Long stockDocId, String stockDocNo, String materialCode, String materialName, BigDecimal qty,
                        boolean reversed, LocalDateTime createdAt) {
    }

    /** @param kitQty 已发材料可生产的数量（齐套数） */
    public record OsDetail(Long id, String docNo, LocalDate docDate, String status, Long supplierId, String supplierName, Long materialId,
                           String materialCode, String materialName, String materialSpec, String uom, Long bomId, String bomNo, Integer bomVersion,
                           BigDecimal qty, BigDecimal processPrice, BigDecimal taxRate, String currency, BigDecimal exchangeRate, BigDecimal amount,
                           BigDecimal taxAmount, BigDecimal totalAmount, LocalDate requiredDate, BigDecimal receivedQty, BigDecimal qualifiedQty,
                           BigDecimal kitQty, Long mrpResultId, String closeReason, String remark, Long ownerId, String ownerName,
                           LocalDateTime createdAt, Integer version, boolean priceVisible, List<OsMaterialResp> materials,
                           List<OsReceiptLine> receipts, List<OsTxn> txns, List<RelatedDoc> related) {
    }

    public record QtyLine(@NotNull Long outsourcingMaterialId, BigDecimal qty, Boolean defective) {
    }

    public record QtyReq(@Valid List<QtyLine> lines) {
    }

    public record SettleLine(@NotNull Long outsourcingMaterialId, @Size(max = 256) String lossReason) {
    }

    public record SettleReq(@Valid List<SettleLine> lines) {
    }

    /** 发料、退料生成的仓库单据 */
    public record StockDocResult(List<Long> stockDocIds) {
    }
}
