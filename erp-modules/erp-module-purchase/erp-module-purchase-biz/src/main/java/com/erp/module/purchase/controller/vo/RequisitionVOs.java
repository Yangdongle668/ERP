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

/** 采购申请（需求 07-03） */
public final class RequisitionVOs {

    private RequisitionVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReqQuery extends PageParam {
        private String docNo;
        private String requisitionType;
        private String statuses;
        private Long requestDeptId;
        private Long ownerId;
        private Long materialId;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        private Boolean urgent;
    }

    public record ReqRow(Long id, String docNo, LocalDate docDate, String requisitionType, Long requestDeptId, String requestDeptName, Long ownerId,
                         String ownerName, String materialSummary, int lineCount, int orderedLineCount, boolean urgent, LocalDate earliestRequiredDate,
                         String status) {
    }

    public record ReqLineSave(@NotNull(message = "请选择物料") Long materialId, String uom, BigDecimal qty, LocalDate requiredDate,
                              Long suggestedSupplierId, @Size(max = 256) String purpose, @Size(max = 256) String remark) {
    }

    public record ReqSave(String requisitionType, Long requestDeptId, Boolean urgent, @Size(max = 1000) String remark,
                          @Valid List<ReqLineSave> lines, List<Long> fileIds, Integer version) {
    }

    public record ReqLineResp(Long id, Integer lineNo, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                              String uom, BigDecimal qty, BigDecimal baseQty, LocalDate requiredDate, Long suggestedSupplierId,
                              String suggestedSupplierName, BigDecimal referencePrice, String purpose, BigDecimal orderedQty, String lineStatus,
                              Long mrpResultId, String sourceDemand, String remark) {
    }

    public record ReqDetail(Long id, String docNo, LocalDate docDate, String status, String requisitionType, Long requestDeptId, String requestDeptName,
                            boolean urgent, Long mrpRunId, Long ownerId, String ownerName, String remark, LocalDateTime createdAt, Integer version,
                            boolean priceVisible, List<ReqLineResp> lines, List<RelatedDoc> related) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PendingQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private Long materialId;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        /** 只看这些申请行（逗号分隔） */
        private String lineIds;
    }

    /** 待转订单明细（基本单位） */
    public record PendingLine(Long id, Long requisitionId, String docNo, Integer lineNo, String requisitionType, boolean urgent, Long materialId,
                              String materialCode, String materialName, String materialSpec, String baseUom, BigDecimal qty, BigDecimal orderedQty,
                              BigDecimal pendingQty, LocalDate requiredDate, Integer leadTimeDays, Long suggestedSupplierId, String suggestedSupplierName,
                              BigDecimal referencePrice, String ownerName, String sourceDemand) {
    }
}
