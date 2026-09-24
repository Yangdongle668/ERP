package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.OrderPolicy;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建/修改物料（需求 05-02 3.2）。属性组字段为空时取默认值（新建）或保持原值不变（修改时仍会按请求写入，前端提交完整表单）。
 *
 * @param code    为空时按编码规则自动生成
 * @param version 修改时必须回传读取时的版本号（乐观锁）
 */
public record MaterialSaveReqVO(
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "编码只能包含字母、数字和 . _ -") String code,
        @NotBlank(message = "物料名称不能为空") @Size(max = 128) String name,
        @Size(max = 256) String nameEn,
        @Size(max = 512) String spec,
        @NotNull(message = "物料类型不能为空") MaterialType materialType,
        @NotNull(message = "请选择物料类别") Long categoryId,
        @NotBlank(message = "基本单位不能为空") @Size(max = 16) String baseUom,
        @Size(max = 64) String drawingNo,
        @Size(max = 16) String revision,
        @Size(max = 64) String brand,
        @Size(max = 128) String manufacturer,
        @Size(max = 128) String mpn,
        @Pattern(regexp = "^(\\d{4,10})?$", message = "海关编码为 4～10 位数字") String hsCode,
        @DecimalMin(value = "0", message = "单位净重不能小于 0") BigDecimal unitNetWeight,
        @DecimalMin(value = "0", message = "单位毛重不能小于 0") BigDecimal unitGrossWeight,
        Long imageFileId,
        @Size(max = 512) String remark,
        // 计划属性
        SourceType sourceType,
        @Min(value = 0, message = "提前期为 0～365 天") @Max(value = 365, message = "提前期为 0～365 天") Integer leadTimeDays,
        @DecimalMin(value = "0", message = "安全库存不能小于 0") BigDecimal safetyStock,
        @DecimalMin(value = "0", message = "最高库存不能小于 0") BigDecimal maxStock,
        OrderPolicy orderPolicy,
        @DecimalMin(value = "0", inclusive = false, message = "固定批量必须大于 0") BigDecimal fixedLotQty,
        @Min(value = 1, message = "合并周期为 1～90 天") @Max(value = 90, message = "合并周期为 1～90 天") Integer periodDays,
        @DecimalMin(value = "0", message = "MOQ 不能小于 0") BigDecimal moq,
        @DecimalMin(value = "0", message = "MPQ 不能小于 0") BigDecimal mpq,
        Long plannerId,
        // 采购属性
        Long buyerId,
        @Size(max = 16) String purchaseUom,
        @DecimalMin(value = "0", message = "允许超收比例为 0～100%") @DecimalMax(value = "1", message = "允许超收比例为 0～100%") BigDecimal overReceivePct,
        // 库存属性
        Tracking tracking,
        IssueRule issueRule,
        @Min(value = 1, message = "保质期为 1～3650 天") @Max(value = 3650, message = "保质期为 1～3650 天") Integer shelfLifeDays,
        @DecimalMin(value = "0", message = "最小剩余保质期为 0～100%") @DecimalMax(value = "1", message = "最小剩余保质期为 0～100%") BigDecimal minRemainingLifePct,
        // 质量属性
        Boolean iqcRequired,
        Boolean fqcRequired,
        Boolean oqcRequired,
        // 财务与销售
        @DecimalMin(value = "0", message = "标准成本不能小于 0") BigDecimal standardCost,
        @Size(max = 16) String salesUom,
        @DecimalMin(value = "0", message = "税率为 0～100%") @DecimalMax(value = "1", message = "税率为 0～100%") BigDecimal purchaseTaxRate,
        @DecimalMin(value = "0", message = "税率为 0～100%") @DecimalMax(value = "1", message = "税率为 0～100%") BigDecimal salesTaxRate,
        @Valid List<UomSave> uoms,
        Integer version) {

    public record UomSave(@NotBlank(message = "请选择辅助单位") String uom,
                          @NotNull(message = "请输入换算率") @DecimalMin(value = "0", inclusive = false, message = "换算率必须大于 0") BigDecimal rate,
                          @Size(max = 128) String remark) {
    }

    /** 兼容旧调用：只含基本信息 */
    public static MaterialSaveReqVO basic(String code, String name, String nameEn, String spec, MaterialType type, Long categoryId,
                                          String baseUom, String remark, Integer version) {
        return new MaterialSaveReqVO(code, name, nameEn, spec, type, categoryId, baseUom, null, null, null, null, null, null, null, null, null,
                remark, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, version);
    }
}
