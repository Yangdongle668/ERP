package com.erp.module.purchase.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购退货（需求 07-08） */
public final class ReturnVOs {

    private ReturnVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReturnQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private String returnReason;
        private String handling;
        private String statuses;
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record ReturnRow(Long id, String docNo, LocalDate docDate, Long supplierId, String supplierName, String returnReason, String handling,
                            Long warehouseId, String warehouseName, String materialSummary, String currency, BigDecimal totalAmount, String status,
                            String outStatus, String ownerName) {
    }

    public record ReturnLineSave(@NotNull(message = "请选择到货记录") Long receiptLineId, @Size(max = 64) String batchNo,
                                 @NotNull(message = "请填写退货数量") BigDecimal qty, @Size(max = 256) String remark) {
    }

    public record ReturnSave(@NotNull(message = "请选择供应商") Long supplierId, @NotBlank(message = "请选择退货原因") String returnReason,
                             @NotBlank(message = "请选择处理方式") String handling, @NotNull(message = "请选择出库仓") Long warehouseId,
                             @Size(max = 64) String ncrNo, @Size(max = 1000) String remark, @Valid List<ReturnLineSave> lines, List<Long> fileIds,
                             Integer version) {
    }

    public record ReturnLineResp(Long id, Integer lineNo, Long receiptLineId, Long receiptId, String receiptNo, Long orderLineId, String orderNo,
                                 Long materialId, String materialCode, String materialName, String materialSpec, String baseUom, String batchNo,
                                 BigDecimal qty, BigDecimal returnableQty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal totalAmount,
                                 BigDecimal outQty, LocalDate outDate, BigDecimal statementQty, String remark) {
    }

    public record ReturnDetail(Long id, String docNo, LocalDate docDate, String status, Long supplierId, String supplierName, String returnReason,
                               String handling, Long warehouseId, String warehouseName, String currency, BigDecimal exchangeRate, BigDecimal totalAmount,
                               Long stockOutId, String stockOutNo, String ncrNo, String voidReason, String remark, Long ownerId, String ownerName,
                               LocalDateTime createdAt, Integer version, boolean priceVisible, List<ReturnLineResp> lines, List<RelatedDoc> related) {
    }

    /** 从不良品生成的候选：不良品仓有库存且能追溯到采购到货的批次 */
    public record DefectCandidate(Long receiptLineId, Long supplierId, String supplierName, Long receiptId, String receiptNo, String orderNo,
                                  Long materialId, String materialCode, String materialName, String baseUom, String batchNo, Long warehouseId,
                                  String warehouseName, BigDecimal ngQty, BigDecimal rejectedQty, BigDecimal returnableQty, String inspectStatus,
                                  String inspectionNo) {
    }

    public record DefectItem(@NotNull Long receiptLineId, @NotNull Long warehouseId, BigDecimal qty) {
    }

    public record FromDefectsReq(@Valid List<DefectItem> items) {
    }
}
