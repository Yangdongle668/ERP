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

/** 应收与销项发票（12-02） */
public final class ArVOs {

    private ArVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ArQuery extends PageParam {
        private String docNo;
        private Long customerId;
        /** 逗号分隔 SALES / SALES_RETURN / DISCOUNT / OTHER */
        private String arTypes;
        /** 逗号分隔 DRAFT / PENDING / CONFIRMED / VOIDED */
        private String statuses;
        private LocalDate bizDateFrom;
        private LocalDate bizDateTo;
        private String sourceNo;
        private String orderNo;
        private String currency;
        /** NONE 未核销 / PARTIAL 部分 / FULL 已核销 / OPEN 未核销完 */
        private String verifyState;
        /** NONE / PARTIAL / FULL / OPEN */
        private String invoiceState;
        private LocalDate dueFrom;
        private LocalDate dueTo;
        private Boolean overdueOnly;
    }

    public record ArRow(Long id, String docNo, String arType, Long customerId, String customerName, String sourceType, Long sourceId, String sourceNo,
                        LocalDate bizDate, String currency, BigDecimal exchangeRate, BigDecimal totalAmount, BigDecimal totalAmountBase,
                        BigDecimal verifiedAmount, BigDecimal unverifiedAmount, BigDecimal invoicedAmount, LocalDate dueDate, int overdueDays,
                        String status, String ownerName, String description, LocalDateTime createdAt) {
    }

    public record ArLineVO(Long id, int lineNo, Long orderId, String orderNo, Long orderLineId, Long materialId, String materialCode, String materialName,
                           String description, BigDecimal qty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount,
                           BigDecimal totalAmount, BigDecimal invoicedQty, BigDecimal invoicedAmount) {
    }

    public record InvoiceRef(Long id, String docNo, String invoiceNo, String invoiceType, LocalDate invoiceDate, BigDecimal qty, BigDecimal totalAmount,
                             String status) {
    }

    public record ArDetail(ArRow header, BigDecimal amount, BigDecimal taxAmount, Long paymentTermId, Long orderId, LocalDate blDate,
                           LocalDateTime confirmedAt, String voidReason, Long voucherId, String remark, List<ArLineVO> lines,
                           List<VerifyVOs.VerificationVO> verifications, List<InvoiceRef> invoices) {
    }

    public record OtherLine(@NotBlank String description, @NotNull BigDecimal totalAmount, BigDecimal taxRate) {
    }

    public record OtherArSave(@NotNull Long customerId, @NotBlank String currency, BigDecimal exchangeRate, @NotNull LocalDate bizDate, LocalDate dueDate,
                              @NotBlank String description, String remark, List<Long> fileIds, List<OtherLine> lines) {
    }

    public record SubmitResult(Long id, String status) {
    }

    // ==================== 销项发票 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InvoiceQuery extends PageParam {
        private String keyword;
        private Long customerId;
        private String invoiceType;
        private String status;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record InvoiceRow(Long id, String docNo, Long customerId, String customerName, String invoiceType, String invoiceNo, LocalDate invoiceDate,
                             String currency, BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, String status, String voidReason,
                             String remark, String createdByName, LocalDateTime createdAt) {
    }

    public record InvoiceLineVO(Long id, int lineNo, Long receivableId, String receivableNo, Long receivableLineId, Long orderLineId, String orderNo,
                                Long materialId, String materialCode, String materialName, BigDecimal qty, BigDecimal amount, BigDecimal taxAmount,
                                BigDecimal totalAmount) {
    }

    public record InvoiceDetail(InvoiceRow header, List<InvoiceLineVO> lines) {
    }

    /** 可开票的应收行（已确认、未完全开票的蓝字应收） */
    public record UninvoicedLine(Long receivableId, String receivableNo, Long receivableLineId, String sourceNo, LocalDate bizDate, String currency,
                                 Long orderLineId, String orderNo, Long materialId, String materialCode, String materialName, String description,
                                 BigDecimal qty, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal totalAmount, BigDecimal invoicedQty,
                                 BigDecimal uninvoicedQty, BigDecimal uninvoicedAmount) {
    }

    /** @param totalAmount 本次开票价税合计（为空按数量比例计算；可调整尾差 ≤ 1 元） */
    public record InvoiceLineReq(@NotNull Long receivableLineId, @NotNull BigDecimal qty, BigDecimal totalAmount) {
    }

    public record InvoiceSave(@NotNull Long customerId, @NotBlank String invoiceType, @NotBlank String invoiceNo, @NotNull LocalDate invoiceDate,
                              String remark, List<Long> fileIds, List<InvoiceLineReq> lines) {
    }
}
