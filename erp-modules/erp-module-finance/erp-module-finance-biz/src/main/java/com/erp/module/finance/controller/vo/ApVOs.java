package com.erp.module.finance.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 应付与进项发票（12-04） */
public final class ApVOs {

    private ApVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ApQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        /** 逗号分隔 PURCHASE / OUTSOURCE / OTHER */
        private String apTypes;
        private String statuses;
        private LocalDate bizDateFrom;
        private LocalDate bizDateTo;
        private String statementNo;
        private String currency;
        private LocalDate dueFrom;
        private LocalDate dueTo;
        private Boolean overdueOnly;
        /** 发票：NONE 未匹配 / PARTIAL 部分 / FULL 全部 / OPEN 未全部 */
        private String invoiceState;
        /** 付款：NONE / PARTIAL / FULL / OPEN */
        private String payState;
    }

    public record ApRow(Long id, String docNo, String apType, Long supplierId, String supplierName, Long statementId, String statementNo,
                        LocalDate bizDate, String currency, BigDecimal exchangeRate, BigDecimal totalAmount, BigDecimal totalAmountBase,
                        BigDecimal invoicedAmount, BigDecimal requestedAmount, BigDecimal verifiedAmount, BigDecimal unpaidAmount,
                        BigDecimal requestableAmount, LocalDate dueDate, int overdueDays, String status, String ownerName, String description,
                        LocalDateTime createdAt) {
    }

    public record ApLineVO(Long id, int lineNo, String lineType, String sourceNo, String orderNo, Long materialId, String materialCode,
                           String materialName, String description, BigDecimal qty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal amount,
                           BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal invoicedQty, BigDecimal invoicedAmount) {
    }

    public record InvoiceMatchRef(Long invoiceId, String docNo, String invoiceNo, LocalDate invoiceDate, String matchStatus, BigDecimal qty,
                                  BigDecimal totalAmount, String status) {
    }

    public record RequestRef(Long requestId, String docNo, String status, BigDecimal amount, BigDecimal paidAmount, LocalDate planPayDate) {
    }

    public record ApDetail(ApRow header, BigDecimal amount, BigDecimal taxAmount, LocalDateTime confirmedAt, String voidReason, Long voucherId,
                           String remark, List<ApLineVO> lines, List<InvoiceMatchRef> invoices, List<RequestRef> requests,
                           List<VerifyVOs.VerificationVO> verifications) {
    }

    public record OtherApSave(@NotNull Long supplierId, String apType, @NotBlank String currency, BigDecimal exchangeRate, @NotNull LocalDate bizDate,
                              LocalDate dueDate, @NotBlank String description, String remark, List<Long> fileIds, List<ArVOs.OtherLine> lines) {
    }

    /** 付款申请可选应付：已确认、可申请金额 > 0（按到期日） */
    public record PayableCandidate(Long payableId, String docNo, String statementNo, LocalDate bizDate, LocalDate dueDate, int overdueDays,
                                   String currency, BigDecimal totalAmount, BigDecimal invoicedAmount, BigDecimal requestedAmount,
                                   BigDecimal verifiedAmount, BigDecimal requestableAmount, boolean uninvoiced) {
    }

    // ==================== 进项发票 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PiQuery extends PageParam {
        private String keyword;
        private Long supplierId;
        private String invoiceType;
        /** UNMATCHED / MATCHED / DIFF */
        private String matchStatus;
        /** NOT_CERTIFIED / CERTIFIED */
        private String deductionStatus;
        private String status;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record PiRow(Long id, String docNo, Long supplierId, String supplierName, String invoiceType, String invoiceNo, String invoiceCode,
                        LocalDate invoiceDate, String currency, BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, String matchStatus,
                        String deductionStatus, String certifiedPeriod, String status, String remark, String createdByName, LocalDateTime createdAt) {
    }

    public record PiLineVO(Long id, int lineNo, Long payableId, String payableNo, Long payableLineId, String sourceNo, String orderNo, Long materialId,
                           String materialCode, String materialName, BigDecimal qty, BigDecimal invoicePrice, BigDecimal apPrice, BigDecimal amount,
                           BigDecimal taxAmount, BigDecimal totalAmount, BigDecimal apAmount, BigDecimal priceDiffPct, boolean overTolerance,
                           String diffReason) {
    }

    public record PiDetail(PiRow header, String diffConfirmedByName, LocalDateTime diffConfirmedAt, String voidReason, List<PiLineVO> lines) {
    }

    /**
     * @param qty          本次发票数量（应付行无数量时忽略）
     * @param invoicePrice 发票不含税单价（应付行无数量时为不含税金额）；为空取应付单价
     */
    public record PiLineReq(@NotNull Long payableLineId, BigDecimal qty, BigDecimal invoicePrice, String diffReason) {
    }

    public record PiSave(@NotNull Long supplierId, @NotBlank String invoiceType, @NotBlank String invoiceNo, String invoiceCode,
                         @NotNull LocalDate invoiceDate, @NotNull BigDecimal totalAmount, @NotNull BigDecimal taxAmount, String remark,
                         List<Long> fileIds, List<PiLineReq> lines) {
    }

    /** 可开票应付行 */
    public record UninvoicedApLine(Long payableId, String payableNo, Long payableLineId, String statementNo, String sourceNo, String orderNo,
                                   LocalDate bizDate, String currency, String lineType, Long materialId, String materialCode, String materialName,
                                   String description, BigDecimal qty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal apPrice,
                                   BigDecimal totalAmount, BigDecimal invoicedQty, BigDecimal uninvoicedQty, BigDecimal uninvoicedAmount) {
    }

    public record CertifyReq(@NotBlank String period) {
    }
}
