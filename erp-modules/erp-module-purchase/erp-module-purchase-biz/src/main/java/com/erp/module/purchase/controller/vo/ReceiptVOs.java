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

/** 到货（需求 07-06） */
public final class ReceiptVOs {

    private ReceiptVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReceiptQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private String deliveryNoteNo;
        private String orderNo;
        private Long materialId;
        private String inspectStatus;
        private String receiptType;
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    /** @param stockStatus ALL 全部入库 / PARTIAL 部分 / NONE 未入库 */
    public record ReceiptRow(Long id, String docNo, String receiptType, Long supplierId, String supplierName, String deliveryNoteNo,
                             LocalDateTime arrivalAt, String materialSummary, int lineCount, String inspectSummary, String stockStatus,
                             String receiverName, String status) {
    }

    /** 采购/样品到货：orderLineId 必填；委外收货：orderId 为委外单 */
    public record ReceiptLineSave(Long orderLineId, Long orderId, @NotNull(message = "请填写到货数量") BigDecimal qty,
                                  @Size(max = 64) String supplierBatchNo, LocalDate productionDate, @Size(max = 256) String remark) {
    }

    public record ReceiptSave(String receiptType, @NotNull(message = "请选择供应商") Long supplierId, @Size(max = 64) String deliveryNoteNo,
                              LocalDateTime arrivalAt, Long receiverId, @Size(max = 1000) String remark, @Valid List<ReceiptLineSave> lines,
                              List<Long> fileIds, Integer version) {
    }

    public record ReceiptLineResp(Long id, Integer lineNo, Long orderId, String orderNo, Long orderLineId, Integer orderLineNo, Long materialId,
                                  String materialCode, String materialName, String materialSpec, String baseUom, String uom, BigDecimal qty,
                                  BigDecimal baseQty, BigDecimal openQty, String supplierBatchNo, LocalDate productionDate, boolean inspectRequired,
                                  Long targetWarehouseId, String targetWarehouseName, Long stockInId, String stockInNo, BigDecimal stockedQty,
                                  LocalDate stockedDate, String batchNo, String inspectStatus, BigDecimal qualifiedQty, BigDecimal concessionQty,
                                  BigDecimal rejectedQty, BigDecimal returnedQty, BigDecimal statementQty, String inspectionNo, String rejectReason,
                                  String remark) {
    }

    public record ReceiptDetail(Long id, String docNo, LocalDate docDate, String status, String receiptType, Long supplierId, String supplierName,
                                String supplierStatus, String deliveryNoteNo, LocalDateTime arrivalAt, Long receiverId, String receiverName,
                                String remark, Long ownerId, String createdByName, LocalDateTime createdAt, Integer version,
                                List<ReceiptLineResp> lines, List<RelatedDoc> related) {
    }

    /** 到货选单（退货用）：该供应商已入库的到货行 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReturnableQuery extends PageParam {
        private Long supplierId;
        private Long materialId;
        private String receiptNo;
    }

    public record ReturnableLine(Long id, Long receiptId, String receiptNo, LocalDate arrivalDate, Long orderId, String orderNo, Long orderLineId,
                                 Long materialId, String materialCode, String materialName, String baseUom, String batchNo, String currency,
                                 BigDecimal baseQty, BigDecimal stockedQty, BigDecimal rejectedQty, BigDecimal returnedQty, BigDecimal returnableQty,
                                 BigDecimal priceInclTax, String inspectStatus) {
    }
}
