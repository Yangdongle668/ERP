package com.erp.module.quality.controller.vo;

import com.erp.common.result.PageParam;
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

/** NCR 与 MRB */
public final class NcrVOs {

    private NcrVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class NcrQuery extends PageParam {
        private String docNo;
        private String source;
        private Long materialId;
        private Long supplierId;
        private Long customerId;
        private String responsibility;
        private String severity;
        /** DocStatus，逗号分隔；OPEN = 未关闭（草稿、待审批、已审核） */
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Long inspectionId;
    }

    public record NcrRow(Long id, String docNo, String source, String sourceNo, Long materialId, String materialCode, String materialName, String batchNo,
                         BigDecimal ncrQty, String severity, String responsibility, String dispositionSummary, Long supplierId, String supplierName,
                         Long customerId, String customerName, boolean capaRequired, boolean scarRequired, String status, String ownerName,
                         LocalDate docDate, LocalDateTime createdAt) {
    }

    public record DispositionSave(@NotBlank(message = "请选择处置方式") String disposition, @NotNull(message = "请填写处置数量") BigDecimal qty,
                                  @Size(max = 512) String remark) {
    }

    public record NcrSave(String source, Long materialId, @Size(max = 64) String batchNo, BigDecimal ncrQty, Long supplierId, Long customerId,
                          @NotBlank(message = "请填写不合格描述") @Size(max = 2000) String defectDescription, List<String> defectCodes,
                          @NotBlank(message = "请选择严重度") String severity, @NotBlank(message = "请选择责任归属") String responsibility,
                          @Size(max = 1000) String containment, Boolean capaRequired, Boolean scarRequired, List<Long> fileIds,
                          @Valid List<DispositionSave> dispositions, @Size(max = 64) String sourceNo, Integer version) {
    }

    public record DispositionRow(Long id, int seq, String disposition, BigDecimal qty, String remark, String followDocNo, boolean done, LocalDateTime doneAt) {
    }

    public record NcrDetail(Long id, String docNo, LocalDate docDate, String source, String sourceNo, Long inspectionId, String inspectionNo,
                            String inspectionResult, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                            String batchNo, BigDecimal ncrQty, Long supplierId, String supplierName, Long customerId, String customerName,
                            String defectDescription, List<String> defectCodes, String severity, String responsibility, String containment,
                            boolean capaRequired, boolean scarRequired, boolean capaSuggested, BigDecimal amountBase, Long capaId, String capaNo,
                            String capaStatus, Long scarId, String scarNo, String scarStatus, Long complaintId, String complaintNo, boolean batchFrozen,
                            String status, boolean approvalRunning, Long ownerId, String ownerName, LocalDateTime approvedAt, LocalDateTime closedAt,
                            String remark, int version, List<DispositionRow> dispositions) {
    }

    public record DoneReq(@Size(max = 64) String followDocNo) {
    }

    public record CloseReq(Boolean unfreeze) {
    }

    /** CAPA 建议（新建时预判 QC-NCR-R05） */
    public record CapaSuggestReq(Long materialId, List<String> defectCodes, String severity, String source) {
    }
}
