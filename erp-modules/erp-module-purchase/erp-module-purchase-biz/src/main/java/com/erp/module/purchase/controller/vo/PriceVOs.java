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

/** 采购价格与调价单（需求 07-02） */
public final class PriceVOs {

    private PriceVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PriceQuery extends PageParam {
        private Long supplierId;
        private Long materialId;
        /** 物料编码前缀或名称 */
        private String keyword;
        private Long categoryId;
        /** 状态，逗号分隔：EFFECTIVE/EXPIRED/REPLACED；为空默认有效 */
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String columns;
    }

    public record PriceRow(Long id, Long supplierId, String supplierCode, String supplierName, Long materialId, String materialCode,
                           String materialName, String materialSpec, String baseUom, String currency, BigDecimal minQty, BigDecimal price,
                           BigDecimal taxRate, BigDecimal priceInclTax, LocalDate effectiveFrom, LocalDate effectiveTo, String priceStatus,
                           Long adjustId, String adjustNo, LocalDateTime updatedAt) {
    }

    /**
     * 取价结果（按请求的单位换算）：price 不含税单价、priceInclTax 含税单价（每请求单位）；basePrice 为每基本单位不含税价。
     */
    public record EffectivePrice(Long priceId, BigDecimal price, BigDecimal priceInclTax, BigDecimal taxRate, BigDecimal basePrice,
                                 BigDecimal minQty, String currency, LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AdjustQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private Long materialId;
        private String statuses;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record AdjustRow(Long id, String docNo, LocalDate docDate, Long supplierId, String supplierName, String currency, String adjustReason,
                            String source, int lineCount, BigDecimal maxChangePct, String status, String ownerName, LocalDateTime createdAt) {
    }

    public record AdjustLineSave(@NotNull(message = "请选择物料") Long materialId, BigDecimal minQty, BigDecimal newPrice, BigDecimal taxRate,
                                 LocalDate effectiveFrom, LocalDate effectiveTo, @Size(max = 256) String remark) {
    }

    public record AdjustSave(@NotNull(message = "请选择供应商") Long supplierId, String currency,
                             @NotBlank(message = "请填写调价原因") @Size(max = 512) String adjustReason, @Size(max = 1000) String remark,
                             @Valid List<AdjustLineSave> lines, List<Long> fileIds, Integer version) {
    }

    /** @param overThreshold 涨幅超过 10%（R05 标红） */
    public record AdjustLineResp(Long id, Integer lineNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                 String baseUom, BigDecimal minQty, BigDecimal oldPrice, BigDecimal newPrice, BigDecimal taxRate,
                                 BigDecimal changePct, LocalDate effectiveFrom, LocalDate effectiveTo, String remark, boolean overThreshold) {
    }

    public record AdjustDetail(Long id, String docNo, LocalDate docDate, String status, Long supplierId, String supplierName,
                               String supplierStatus, String currency, String adjustReason, String source, Long rfqId, String rfqNo,
                               String remark, String ownerName, LocalDateTime createdAt, Integer version, List<AdjustLineResp> lines) {
    }
}
