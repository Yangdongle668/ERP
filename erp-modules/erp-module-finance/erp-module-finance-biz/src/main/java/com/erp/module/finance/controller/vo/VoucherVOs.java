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

/** 凭证（12-06） */
public final class VoucherVOs {

    private VoucherVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class VoucherQuery extends PageParam {
        private String period;
        private String voucherNo;
        /** 逗号分隔 DRAFT / AUDITED / POSTED */
        private String statuses;
        /** AUTO / MANUAL */
        private String source;
        private String accountCode;
        private String summary;
        private Long creatorId;
    }

    public record VoucherRow(Long id, String voucherNo, String period, LocalDate voucherDate, String summary, BigDecimal totalDebit, BigDecimal totalCredit,
                             int attachmentCount, String source, String bizType, String status, String creatorName, String auditorName, String posterName,
                             LocalDateTime createdAt) {
    }

    public record VoucherLineVO(Long id, int lineNo, String summary, String accountCode, String accountName, BigDecimal debit, BigDecimal credit,
                                String currency, BigDecimal fcAmount, BigDecimal exchangeRate, Long auxCustomerId, String auxCustomerName,
                                Long auxSupplierId, String auxSupplierName, Long auxDeptId, String auxDeptName, Long auxMaterialId, String auxMaterialName,
                                Long auxProjectId, String sourceType, Long sourceId) {
    }

    public record VoucherDetail(VoucherRow header, String remark, Long createdBy, LocalDateTime auditedAt, LocalDateTime postedAt, List<VoucherLineVO> lines) {
    }

    public record VoucherLineReq(String summary, @NotBlank String accountCode, BigDecimal debit, BigDecimal credit, String currency, BigDecimal fcAmount,
                                 BigDecimal exchangeRate, Long auxCustomerId, Long auxSupplierId, Long auxDeptId, Long auxMaterialId, Long auxProjectId) {
    }

    public record VoucherSave(@NotNull LocalDate voucherDate, Integer attachmentCount, String remark, List<VoucherLineReq> lines) {
    }

    /**
     * @param bizTypes SALES_AR / RECEIPT / PURCHASE_AP / PAYMENT / FX_GAIN_LOSS / STOCK_OUT_SALES_COST / PRODUCTION_ISSUE / PRODUCTION_IN
     * @param mode     PER_DOC 按单据生成 / SUMMARY 按类型汇总生成
     */
    public record GenerateReq(@NotBlank String period, List<String> bizTypes, String mode) {
    }

    public record GenerateResult(int voucherCount, int docCount, List<Long> voucherIds, List<String> messages) {
    }

    public record Preview(String bizType, List<VoucherLineVO> lines, BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced) {
    }

    /** 待生成凭证的单据数（生成弹窗） */
    public record Pending(String bizType, String label, int count) {
    }
}
