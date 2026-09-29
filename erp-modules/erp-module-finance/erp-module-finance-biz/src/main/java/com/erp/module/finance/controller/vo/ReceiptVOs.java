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

/** 收款单（12-03） */
public final class ReceiptVOs {

    private ReceiptVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReceiptQuery extends PageParam {
        private String docNo;
        private Long customerId;
        /** 逗号分隔 SALES / ADVANCE / OTHER / REFUND */
        private String receiptTypes;
        private Long bankAccountId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        /** 逗号分隔 DRAFT / CONFIRMED / VOIDED */
        private String statuses;
        /** 仅未核销完 */
        private Boolean openOnly;
        private String orderNo;
    }

    public record ReceiptRow(Long id, String docNo, Long customerId, String customerName, String receiptType, Long bankAccountId, String bankAccountName,
                             String settlementMethod, LocalDate receiptDate, String currency, BigDecimal exchangeRate, BigDecimal amount,
                             BigDecimal bankFee, BigDecimal amountBase, BigDecimal allocatedAmount, BigDecimal unallocatedAmount, Long orderId,
                             String orderNo, String bankRefNo, String payerName, String status, String ownerName, String remark,
                             LocalDateTime createdAt) {
    }

    public record ReceiptDetail(ReceiptRow header, LocalDateTime confirmedAt, Long voucherId, List<VerifyVOs.VerificationVO> verifications) {
    }

    public record ReceiptSave(@NotNull Long customerId, @NotBlank String receiptType, @NotNull Long bankAccountId, @NotBlank String settlementMethod,
                              @NotNull LocalDate receiptDate, BigDecimal exchangeRate, @NotNull BigDecimal amount, BigDecimal bankFee,
                              String bankRefNo, String payerName, Long orderId, String remark, List<Long> fileIds) {
    }

    /** 预收款可选订单（客户未出完订单） */
    public record OrderOption(Long orderId, String orderNo, String currency) {
    }

    /** 银行流水导入结果：生成的草稿收款单与未匹配客户的行 */
    public record BankImportResult(int created, List<Long> receiptIds, List<UnmatchedRow> unmatched) {
    }

    public record UnmatchedRow(int rowNo, LocalDate date, BigDecimal amount, String payerName, String bankRefNo, String message) {
    }
}
