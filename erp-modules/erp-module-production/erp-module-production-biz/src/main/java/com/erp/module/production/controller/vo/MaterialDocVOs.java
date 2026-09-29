package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 领料单、超领单、退料单（需求 09-03）。数量均为基本单位 */
public final class MaterialDocVOs {

    private MaterialDocVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class IssueQuery extends PageParam {
        private String docNo;
        private Long prodOrderId;
        private String prodOrderNo;
        /** NORMAL / OVER / BACKFLUSH */
        private String issueType;
        /** 逗号分隔；为空默认草稿、待审批、已提交 */
        private String statuses;
        private Long warehouseId;
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record IssueRow(Long id, String docNo, String issueType, Long prodOrderId, String prodOrderNo, String productCode, String productName,
                           Long warehouseId, String warehouseName, int lineCount, String status, String stockOutNos, String overReason, String ownerName,
                           LocalDate docDate) {
    }

    public record IssueLineResp(Long id, int lineNo, Long materialLineId, Long materialId, String code, String name, String spec, String uom,
                                BigDecimal requiredQty, BigDecimal lineIssuedQty, BigDecimal openQty, BigDecimal availableQty, BigDecimal requestQty,
                                BigDecimal issuedQty, String remark) {
    }

    public record IssueDetail(Long id, String docNo, String issueType, String status, LocalDate docDate, Long prodOrderId, String prodOrderNo,
                              String prodStatus, Long productId, String productCode, String productName, Long warehouseId, String warehouseName,
                              BigDecimal kitQty, String overReason, String overRemark, Long reportId, String reportNo, String stockOutIds,
                              String stockOutNos, String remark, Long ownerId, String ownerName, LocalDateTime submittedAt, LocalDateTime createdAt,
                              int version, List<IssueLineResp> lines) {
    }

    /** 新建领料时从用料清单带出的行（发料方式为领料、未领 > 0）；requestQty 为默认申请数量 */
    public record IssueCandidate(Long materialLineId, Long materialId, String code, String name, String spec, String uom, String issueMethod,
                                 BigDecimal requiredQty, BigDecimal issuedQty, BigDecimal openQty, BigDecimal pendingQty, Long warehouseId,
                                 String warehouseName, BigDecimal availableQty, BigDecimal requestQty) {
    }

    public record IssueLineSave(@NotNull Long materialLineId, @NotNull(message = "请填写申请数量") BigDecimal requestQty, Long warehouseId,
                                @Size(max = 256) String remark) {
    }

    /** kitQty：按套数领料时的套数（仅记录）；新建时按行上的发料仓拆分为多张领料单 */
    public record IssueSave(@NotNull(message = "请选择生产订单") Long prodOrderId, BigDecimal kitQty, @Size(max = 1000) String remark,
                            @Valid List<IssueLineSave> lines, Integer version) {
    }

    /** 批量按套数领料：kitQty 为空表示领全部未领 */
    public record ByKitReq(@NotNull List<Long> prodOrderIds, BigDecimal kitQty, Boolean submit) {
    }

    public record OverReq(@NotNull(message = "请选择生产订单") Long prodOrderId, @NotNull(message = "请选择用料行") Long materialLineId,
                          @NotNull(message = "请填写超领数量") BigDecimal qty, String overReason, @Size(max = 256) String overRemark, Long warehouseId,
                          Boolean submit) {
    }

    /** 新建结果：可能按仓库拆成多张 */
    public record CreateResult(List<Long> ids, List<String> docNos, List<String> warnings) {
    }

    // ==================== 退料 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReturnQuery extends PageParam {
        private String docNo;
        private Long prodOrderId;
        private String prodOrderNo;
        /** GOOD / DEFECT */
        private String returnType;
        private String statuses;
        private Long warehouseId;
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record ReturnRow(Long id, String docNo, String returnType, Long prodOrderId, String prodOrderNo, String productCode, String productName,
                            Long warehouseId, String warehouseName, int lineCount, BigDecimal totalQty, String status, String stockInNos, String ownerName,
                            LocalDate docDate) {
    }

    public record ReturnLineResp(Long id, int lineNo, Long materialLineId, Long materialId, String code, String name, String spec, String uom,
                                 BigDecimal returnableQty, BigDecimal qty, String batchNo, String defectDesc, BigDecimal receivedQty) {
    }

    public record ReturnDetail(Long id, String docNo, String returnType, String status, LocalDate docDate, Long prodOrderId, String prodOrderNo,
                               String prodStatus, Long productId, String productCode, String productName, Long warehouseId, String warehouseName,
                               String stockInIds, String stockInNos, String remark, Long ownerId, String ownerName, LocalDateTime createdAt, int version,
                               List<ReturnLineResp> lines) {
    }

    /**
     * 可退数量：良品 = max(0, 已领 − 已退 − 理论耗用)，不良 = 已领 − 已退；均扣除其他未入库退料单的数量。
     * batchNos 为该订单领用过（净数量 > 0）的批次，第一个为默认。
     */
    public record ReturnCandidate(Long materialLineId, Long materialId, String code, String name, String spec, String uom, BigDecimal issuedQty,
                                  BigDecimal returnedQty, BigDecimal theoreticalQty, BigDecimal returnableQty, Long warehouseId, String warehouseName,
                                  List<String> batchNos) {
    }

    public record ReturnLineSave(@NotNull Long materialLineId, @NotNull(message = "请填写退料数量") BigDecimal qty, @Size(max = 64) String batchNo,
                                 @Size(max = 256) String defectDesc) {
    }

    /** warehouseId 为空时按类型默认（良品：物料默认仓；不良：不良品仓），不同仓库拆成多张退料单 */
    public record ReturnSave(@NotNull(message = "请选择生产订单") Long prodOrderId, @NotNull(message = "请选择退料类型") String returnType, Long warehouseId,
                             @Size(max = 1000) String remark, @Valid List<ReturnLineSave> lines, Integer version) {
    }
}
