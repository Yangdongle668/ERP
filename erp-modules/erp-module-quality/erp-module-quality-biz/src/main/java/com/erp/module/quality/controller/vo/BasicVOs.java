package com.erp.module.quality.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 检验基础数据：项目库、抽样方案、缺陷代码、检验标准 */
public final class BasicVOs {

    private BasicVOs() {
    }

    // ==================== 项目库 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ItemLibQuery extends PageParam {
        private String keyword;
        private String itemType;
        private String status;
    }

    public record ItemLibRow(Long id, String code, String name, String itemType, String method, String unit, String defectLevel, String tool,
                             String description, String status, LocalDateTime updatedAt) {
    }

    public record ItemLibSave(@NotBlank(message = "请填写编码") @Size(max = 32) String code, @NotBlank(message = "请填写名称") @Size(max = 64) String name,
                              @NotBlank(message = "请选择项目类型") String itemType, @NotBlank(message = "请选择检验方法") String method,
                              @Size(max = 16) String unit, @NotBlank(message = "请选择缺陷等级") String defectLevel, @Size(max = 64) String tool,
                              @Size(max = 512) String description, String status, Integer version) {
    }

    // ==================== 抽样方案 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SamplingQuery extends PageParam {
        private String keyword;
        private String planType;
        private String status;
    }

    public record SamplingRow(Long id, String code, String name, String planType, String inspectionLevel, String aqlCr, String aqlMa, String aqlMi,
                              Integer fixedQty, String status, String remark, LocalDateTime updatedAt) {
    }

    public record SamplingSave(@NotBlank(message = "请填写编码") @Size(max = 32) String code, @NotBlank(message = "请填写名称") @Size(max = 64) String name,
                               @NotBlank(message = "请选择方案类型") String planType, String inspectionLevel, String aqlCr, String aqlMa, String aqlMi,
                               Integer fixedQty, String status, @Size(max = 256) String remark, Integer version) {
    }

    /** 计算预览：已保存方案传 planId，表单预览传方案字段 */
    public record SamplingPreviewReq(Long planId, String planType, String inspectionLevel, String aqlCr, String aqlMa, String aqlMi, Integer fixedQty,
                                     @NotNull(message = "请填写批量") BigDecimal lotQty) {
    }

    /** 一个缺陷等级的判定组 */
    public record LevelPlan(String level, String aql, int n, int ac, int re) {
    }

    /** AQL 抽样表（字码表 + 主表） */
    public record AqlTableView(List<String> levels, List<String> letters, List<String> aqls, List<AqlCodeRow> codes, List<AqlRow> rows) {
    }

    public record AqlCodeRow(Long id, Long lotMin, Long lotMax, String inspectionLevel, String codeLetter, int version) {
    }

    public record AqlRow(Long id, String codeLetter, String aql, String sampleLetter, int sampleSize, int ac, int re, int version) {
    }

    public record AqlCodeSave(String codeLetter, Integer version) {
    }

    public record AqlTableSave(String sampleLetter, Integer sampleSize, Integer ac, Integer re, Integer version) {
    }

    /** 抽样结果（检验单保存快照） */
    public record SamplingResult(Long planId, String planCode, String planName, String planType, String inspectionLevel, String letter,
                                 BigDecimal lotQty, int sampleQty, boolean full, List<LevelPlan> levels, String text) {
    }

    // ==================== 缺陷代码 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DefectCodeQuery extends PageParam {
        private String keyword;
        private String category;
        private String status;
    }

    public record DefectCodeRow(Long id, String code, String name, String category, String defaultLevel, String status, LocalDateTime updatedAt) {
    }

    public record DefectCodeSave(@NotBlank(message = "请填写编码") @Size(max = 32) String code, @NotBlank(message = "请填写名称") @Size(max = 64) String name,
                                 @NotBlank(message = "请选择缺陷分类") String category, @NotBlank(message = "请选择默认等级") String defaultLevel,
                                 String status, Integer version) {
    }

    // ==================== 检验标准 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class StandardQuery extends PageParam {
        private String code;
        private String name;
        private String inspectType;
        private Long materialId;
        private Long categoryId;
        /** DRAFT / EFFECTIVE / OBSOLETE，逗号分隔；为空为全部 */
        private String statuses;
    }

    public record StandardRow(Long id, String code, String name, String inspectType, String scopeType, Long materialId, String materialCode,
                              String materialName, Long categoryId, String categoryName, String operation, Long samplingPlanId, String samplingPlanName,
                              int stdVersion, int itemCount, String status, LocalDateTime updatedAt) {
    }

    public record StandardItemSave(Long libItemId, @Size(max = 64) String name, @Size(max = 256) String spec, BigDecimal target, BigDecimal upperLimit,
                                   BigDecimal lowerLimit, String defectLevel, Long samplingPlanId, Boolean isKey) {
    }

    public record StandardSave(@NotBlank(message = "请填写名称") @Size(max = 128) String name, @NotBlank(message = "请选择检验类型") String inspectType,
                               @NotBlank(message = "请选择适用范围") String scopeType, Long materialId, Long categoryId, String operation,
                               @NotNull(message = "请选择默认抽样方案") Long samplingPlanId, Long fileId, @Size(max = 512) String remark,
                               @Valid List<StandardItemSave> items, Integer version) {
    }

    public record StandardItem(Long id, int seq, Long libItemId, String name, String itemType, String method, String unit, String spec, BigDecimal target,
                               BigDecimal upperLimit, BigDecimal lowerLimit, String defectLevel, Long samplingPlanId, String samplingPlanName, boolean isKey) {
    }

    public record StandardDetail(Long id, String code, String name, String inspectType, String scopeType, Long materialId, String materialCode,
                                 String materialName, Long categoryId, String categoryName, String operation, Long samplingPlanId, String samplingPlanName,
                                 int stdVersion, String status, Long fileId, String remark, LocalDateTime effectiveAt, int version,
                                 List<StandardItem> items, List<StandardRow> versions) {
    }
}
