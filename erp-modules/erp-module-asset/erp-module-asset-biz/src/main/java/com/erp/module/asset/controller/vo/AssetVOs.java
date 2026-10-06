package com.erp.module.asset.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 固定资产（需求 15-固定资产） */
public final class AssetVOs {

    private AssetVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AssetQuery extends PageParam {
        /** 编码 / 名称 / 规格 */
        private String keyword;
        private String assetClass;
        private String companyNo;
        private String assetStatus;
        private Long deptId;
    }

    public record AssetRow(Long id, String code, String companyNo, String assetClass, String name, String nameAbbr, String spec,
                           LocalDate purchaseDate, Long deptId, String deptName, Long custodianId, String custodianName, String location,
                           String supplierName, String customerName, BigDecimal originalValue, Integer usefulLifeMonths, String assetStatus,
                           LocalDate scrappedDate, String scrapReason, String remark, String createdByName, LocalDateTime createdAt, int version) {
    }

    public record AssetSave(
            @NotBlank(message = "请选择所属公司") String companyNo,
            @NotBlank(message = "请选择资产分类") String assetClass,
            @NotBlank(message = "请输入资产名称") @Size(max = 128, message = "资产名称最多 128 个字") String name,
            @NotBlank(message = "请输入名称缩写") @Pattern(regexp = "^[A-Za-z]{1,3}$", message = "名称缩写为 1～3 位英文字母（如冲片机 CPJ），不足 3 位自动用 X 补位") String nameAbbr,
            @Size(max = 256) String spec,
            @NotNull(message = "请选择购置日期") LocalDate purchaseDate,
            Long deptId,
            Long custodianId,
            @Size(max = 128) String location,
            @Size(max = 128) String supplierName,
            @Size(max = 128) String customerName,
            @DecimalMin(value = "0", message = "原值不能小于 0") BigDecimal originalValue,
            @Min(value = 1, message = "使用年限为 1～600 个月") @Max(value = 600, message = "使用年限为 1～600 个月") Integer usefulLifeMonths,
            @Size(max = 512) String remark,
            Integer version) {
    }

    /** 状态变更：IDLE 闲置、USE 启用、REPAIR 送修、REPAIR_END 修复 */
    public record StatusReq(@NotBlank String op, @Size(max = 256) String reason) {
    }

    public record ScrapReq(@NotNull(message = "请选择报废日期") LocalDate scrappedDate,
                           @NotBlank(message = "请填写报废原因") @Size(max = 256) String reason) {
    }

    /** 编码预览：保存前显示将生成的编码前缀（不占用流水号） */
    public record CodePreview(String prefix, String example) {
    }
}
