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

/** 付款申请与付款单（12-05） */
public final class PayVOs {

    private PayVOs() {
    }

    // ==================== 付款申请 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RequestQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        /** PAYABLE / PREPAYMENT */
        private String requestType;
        /** 逗号分隔；TO_PAY = 待付款（已审批 + 部分付款） */
        private String statuses;
        private LocalDate planFrom;
        private LocalDate planTo;
        /** 本周计划付款 */
        private Boolean thisWeek;
    }

    public record RequestRow(Long id, String docNo, String requestType, Long supplierId, String supplierName, String currency, BigDecimal amount,
                             BigDecimal amountBase, BigDecimal paidAmount, BigDecimal unpaidAmount, LocalDate planPayDate, Long orderId, String orderNo,
                             String status, boolean uninvoicedWarning, String ownerName, String reason, LocalDateTime createdAt) {
    }

    public record RequestLineVO(Long id, int lineNo, Long payableId, String payableNo, String statementNo, LocalDate dueDate, BigDecimal payableTotal,
                                BigDecimal amount, BigDecimal paidAmount) {
    }

    public record PaymentRef(Long id, String docNo, LocalDate payDate, BigDecimal amount, String status) {
    }

    public record RequestDetail(RequestRow header, Long supplierBankId, String supplierBankText, LocalDateTime approvedAt, String remark,
                                List<RequestLineVO> lines, List<PaymentRef> payments) {
    }

    public record RequestLineReq(@NotNull Long payableId, @NotNull BigDecimal amount) {
    }

    public record RequestSave(@NotNull Long supplierId, @NotBlank String requestType, @NotBlank String currency, @NotNull LocalDate planPayDate,
                              Long supplierBankId, Long orderId, BigDecimal amount, String reason, String remark, List<Long> fileIds,
                              List<RequestLineReq> lines) {
    }

    /** 保存 / 提交结果：warnings 为未收到发票等提示 */
    public record RequestResult(Long id, String status, List<String> warnings) {
    }

    /** 供应商收款账户 */
    public record SupplierBankOption(Long id, String bankName, String accountName, String accountNo, String swift, String currency, boolean isDefault) {
    }

    /** 预付款可选采购订单 */
    public record PurchaseOrderOption(Long orderId, String orderNo, LocalDate orderDate, String currency, BigDecimal totalAmount, BigDecimal prepaid,
                                      BigDecimal available) {
    }

    // ==================== 付款单 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaymentQuery extends PageParam {
        private String docNo;
        private String requestNo;
        private Long supplierId;
        private Long bankAccountId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String statuses;
    }

    public record PaymentRow(Long id, String docNo, Long requestId, String requestNo, String requestType, Long supplierId, String supplierName,
                             Long bankAccountId, String bankAccountName, String settlementMethod, LocalDate payDate, String currency,
                             BigDecimal exchangeRate, BigDecimal amount, BigDecimal bankFee, BigDecimal amountBase, BigDecimal allocatedAmount,
                             String bankRefNo, String status, String ownerName, String remark, LocalDateTime createdAt) {
    }

    public record PaymentDetail(PaymentRow header, LocalDateTime confirmedAt, Long voucherId, List<VerifyVOs.VerificationVO> verifications) {
    }

    public record PaymentSave(@NotNull Long requestId, @NotNull Long bankAccountId, @NotBlank String settlementMethod, @NotNull LocalDate payDate,
                              BigDecimal exchangeRate, @NotNull BigDecimal amount, BigDecimal bankFee, String bankRefNo, String remark,
                              List<Long> fileIds) {
    }
}
