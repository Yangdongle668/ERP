package com.erp.module.finance.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 核销（12-03 第 3.3 节、12-05 第 3.3 节） */
public final class VerifyVOs {

    private VerifyVOs() {
    }

    public record VerificationVO(Long id, String verifyType, String batchNo, String currency, String docAType, Long docAId, String docANo,
                                 String docBType, Long docBId, String docBNo, BigDecimal amount, BigDecimal amountBaseA, BigDecimal amountBaseB,
                                 BigDecimal fxDiff, String period, LocalDateTime verifiedAt, String operatorName, boolean reversed,
                                 LocalDateTime reversedAt) {
    }

    /**
     * 核销候选单据
     *
     * @param docType   RECEIPT / RECEIVABLE / PAYMENT / PAYABLE
     * @param kind      左侧：RECEIPT 收款 / ADVANCE 预收 / REFUND 退款 / RED 红字应收；PAYMENT 付款 / PREPAY 预付 / RED 红字应付；右侧：BLUE
     * @param available 可核销金额（原币，红字、退款为负）
     */
    public record Candidate(String docType, Long docId, String docNo, String kind, LocalDate docDate, LocalDate dueDate, Long orderId, String orderNo,
                            String sourceNo, BigDecimal totalAmount, BigDecimal available, BigDecimal exchangeRate, String remark) {
    }

    public record Candidates(Long partnerId, String partnerName, String currency, List<Candidate> left, List<Candidate> right) {
    }

    /** @param amount 本次核销金额（取绝对值，方向由单据决定） */
    public record Pick(@NotBlank String docType, @NotNull Long docId, @NotNull BigDecimal amount) {
    }

    public record VerifyReq(@NotNull Long partnerId, @NotBlank String currency, List<Pick> left, List<Pick> right) {
    }

    public record VerifyResult(String batchNo, int count, BigDecimal amount, BigDecimal fxDiff) {
    }
}
