package com.erp.module.purchase.controller.vo;

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

/** 询价比价（需求 07-04） */
public final class RfqVOs {

    private RfqVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RfqQuery extends PageParam {
        private String docNo;
        private String title;
        private String statuses;
        private Long materialId;
        private Long supplierId;
        private LocalDate deadlineFrom;
        private LocalDate deadlineTo;
    }

    public record RfqRow(Long id, String docNo, String title, String currency, int materialCount, int supplierCount, int quotedCount,
                         LocalDate quoteDeadline, boolean overdue, String status, String ownerName, LocalDate docDate) {
    }

    public record RfqLineSave(@NotNull(message = "请选择物料") Long materialId, BigDecimal qty, LocalDate requiredDate, @Size(max = 256) String remark) {
    }

    public record RfqSave(@NotBlank(message = "请填写标题") @Size(max = 128) String title, String currency, LocalDate quoteDeadline,
                          @Size(max = 1000) String remark, @Valid List<RfqLineSave> lines, List<Long> supplierIds, List<Long> fileIds, Integer version) {
    }

    public record RfqLineResp(Long id, Integer lineNo, Long materialId, String materialCode, String materialName, String materialSpec, String baseUom,
                              BigDecimal qty, LocalDate requiredDate, String remark) {
    }

    public record RfqSupplierResp(Long id, Long supplierId, String supplierCode, String supplierName, String supplierStatus, LocalDateTime sentAt,
                                  boolean quoted) {
    }

    public record RfqDetail(Long id, String docNo, LocalDate docDate, String status, String title, String currency, LocalDate quoteDeadline,
                            String cancelReason, String remark, Long ownerId, String ownerName, LocalDateTime createdAt, Integer version,
                            List<RfqLineResp> lines, List<RfqSupplierResp> suppliers, List<Long> adjustIds) {
    }

    /** 报价单元格 */
    public record QuoteCell(Long supplierId, BigDecimal price, BigDecimal taxRate, BigDecimal moq, Integer leadTimeDays, LocalDate validUntil,
                            boolean awarded, BigDecimal awardQtyPct, String remark, boolean lowest) {
    }

    /**
     * 比价矩阵的一行（物料）：currentPrice 为当前有效价（询价供应商中最低），diffPct 为最低报价与当前有效价的差异（小数）
     */
    public record QuoteRow(Long rfqLineId, Long materialId, String materialCode, String materialName, String baseUom, BigDecimal qty,
                           BigDecimal currentPrice, BigDecimal lowestPrice, BigDecimal diffPct, List<QuoteCell> cells) {
    }

    /** 按供应商汇总：若所有物料都向该供应商采购的总金额（不含税）；complete 为是否对全部物料报价 */
    public record SupplierTotal(Long supplierId, String supplierName, BigDecimal totalAmount, boolean complete) {
    }

    public record QuoteMatrix(Long rfqId, String status, String currency, LocalDate quoteDeadline, boolean pastDeadline,
                              List<RfqSupplierResp> suppliers, List<QuoteRow> rows, List<SupplierTotal> totals) {
    }

    public record QuoteSave(@NotNull Long rfqLineId, @NotNull Long supplierId, BigDecimal price, BigDecimal taxRate, BigDecimal moq,
                            Integer leadTimeDays, LocalDate validUntil, @Size(max = 256) String remark) {
    }

    public record QuotesReq(@Valid List<QuoteSave> quotes) {
    }

    /** 保存报价的结果：已过截止日期时提示（R03） */
    public record QuotesResult(int saved, List<String> warnings) {
    }

    public record AwardSupplier(@NotNull Long supplierId, BigDecimal pct) {
    }

    public record AwardLine(@NotNull Long rfqLineId, @Valid List<AwardSupplier> awards) {
    }

    public record AwardReq(@Valid List<AwardLine> lines) {
    }

    /** 定标结果：每家中标供应商一张草稿调价单 */
    public record AwardResult(List<Long> adjustIds) {
    }
}
