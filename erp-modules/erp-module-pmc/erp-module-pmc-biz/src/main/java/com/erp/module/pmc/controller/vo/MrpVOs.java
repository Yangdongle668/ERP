package com.erp.module.pmc.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** MRP 运算与建议（需求 06-03、06-04） */
public final class MrpVOs {

    private MrpVOs() {
    }

    /** 运算请求：为空的选项取系统参数；runType：FULL / NET_CHANGE / ORDER */
    public record RunReq(String runType, List<Long> orderLineIds, Integer horizonDays, Boolean includeForecast, Boolean includeSafety,
                         Boolean useSubstitute) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RunQuery extends PageParam {
        private String runStatus;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record RunRow(Long id, String runNo, String runType, String runStatus, int progress, LocalDateTime startedAt, LocalDateTime finishedAt,
                         Long durationSeconds, int materialCount, int suggestionCount, int exceptionCount, Long operatorId, String operatorName,
                         String errorMsg, String params, boolean latest) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SuggestionQuery extends PageParam {
        /** 为空取最近一次成功运算 */
        private Long runId;
        /** PURCHASE / MAKE / OUTSOURCE */
        private String type;
        private Long materialId;
        private Long categoryId;
        private Long plannerId;
        private Long buyerId;
        private Long supplierId;
        private LocalDate releaseFrom;
        private LocalDate releaseTo;
        private Boolean lateOnly;
        /** 逗号分隔；为空默认待处理 */
        private String statuses;
    }

    /**
     * @param lateDays 下达已延迟天数（原下达日期早于今天）；sourceSummary 需求来源摘要
     */
    public record SuggestionRow(Long id, Long runId, String type, Long materialId, String materialCode, String materialName, String materialSpec,
                                String baseUom, BigDecimal qty, BigDecimal originalQty, BigDecimal netRequirement, LocalDate requiredDate,
                                LocalDate releaseDate, boolean late, int lateDays, Long supplierId, String supplierName, Long plannerId, String plannerName,
                                Long buyerId, String buyerName, Long bomId, Long deptId, BigDecimal availableQty, BigDecimal inTransitQty,
                                String sourceSummary, String status, String convertedDocType, Long convertedDocId, String convertedDocNo,
                                String ignoreReason, BigDecimal moq, BigDecimal mpq, int version) {
    }

    public record SuggestionUpdate(BigDecimal qty, LocalDate requiredDate, Long supplierId, Long bomId, Long deptId) {
    }

    /** @param release 生产建议：同时下达 */
    public record ConvertReq(List<Long> ids, Boolean release) {
    }

    public record IgnoreReq(List<Long> ids, @Size(max = 256) String reason) {
    }

    public record PegRow(Long id, String demandType, Long sourceId, String sourceNo, Long parentMaterialId, String parentCode, String parentName,
                         Long parentResultId, BigDecimal qty, LocalDate requiredDate) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ExceptionQuery extends PageParam {
        private Long runId;
        /** EXPEDITE / DEFER / CANCEL / PAST_DUE / DISABLED */
        private String type;
        private Long materialId;
        private Boolean handled;
        private Long ownerId;
    }

    public record ExceptionRow(Long id, Long runId, String type, Long materialId, String materialCode, String materialName, String docType,
                               Long docId, String docNo, LocalDate supplyDate, LocalDate suggestedDate, BigDecimal qty, String message, Long ownerId,
                               String ownerName, boolean handled, LocalDateTime pushedAt) {
    }

    public record BalanceRow(LocalDate date, String type, String docNo, Long parentMaterialId, String parentCode, BigDecimal demandQty,
                             BigDecimal supplyQty, BigDecimal projectedQty, BigDecimal safetyStock) {
    }

    public record Balance(Long runId, String runNo, Long materialId, String materialCode, String materialName, String baseUom, BigDecimal safetyStock,
                          List<BalanceRow> rows) {
    }
}
