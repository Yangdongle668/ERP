package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.Tracking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 物料类别接口的请求/响应（需求 05-01） */
public final class CategoryVOs {

    private CategoryVOs() {
    }

    /** 树形表格节点；materialCount 为本类别（含下级）启用物料数 */
    public record CategoryNode(Long id, Long parentId, String code, String name, String codePrefix, MaterialType defaultMaterialType,
                               String defaultBaseUom, Tracking defaultTracking, boolean defaultIqcRequired, Integer defaultShelfLifeDays,
                               int level, int sort, String status, String remark, int materialCount, boolean hasMaterial,
                               int version, Integer codeSeqLength, int segmentCount, List<CategoryNode> children) {
    }

    /** 选择器节点；leaf 为末级（物料只能挂在末级） */
    public record SimpleNode(Long id, Long parentId, String code, String name, String codePrefix, MaterialType defaultMaterialType,
                             String defaultBaseUom, Tracking defaultTracking, boolean defaultIqcRequired, Integer defaultShelfLifeDays,
                             boolean leaf, Integer codeSeqLength, int segmentCount, List<SimpleNode> children) {
    }

    public record CategorySave(
            Long parentId,
            @NotBlank(message = "请输入类别编码") @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "类别编码为 1～16 位字母、数字") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @NotBlank(message = "请输入编码前缀") @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,7}$", message = "编码前缀为 1～8 位字母、数字或 -，不能以 - 开头") String codePrefix,
            @Min(value = 1, message = "流水号位数为 1～8") @Max(value = 8, message = "流水号位数为 1～8") Integer codeSeqLength,
            @NotNull(message = "请选择默认物料类型") MaterialType defaultMaterialType,
            @Size(max = 16) String defaultBaseUom,
            @NotNull(message = "请选择默认库存管理方式") Tracking defaultTracking,
            @NotNull Boolean defaultIqcRequired,
            @Min(value = 1, message = "保质期为 1～3650 天") @Max(value = 3650, message = "保质期为 1～3650 天") Integer defaultShelfLifeDays,
            @NotNull Integer sort,
            @Size(max = 256) String remark,
            Integer version) {
    }

    // ==================== 编码段（需求 05-01 第 8 节） ====================

    public record SegmentValue(Long id, String code, String name, int sort, String status, String remark) {
    }

    public record Segment(Long id, String name, int length, int sort, String remark, List<SegmentValue> values) {
    }

    /** 类别的编码方案；locked 为已有物料（只能改名称、新增 / 停用特征值） */
    public record CodeScheme(Long categoryId, String categoryName, String codePrefix, Integer codeSeqLength, boolean leaf, boolean locked,
                             List<Segment> segments) {
    }

    public record SegmentValueSave(
            Long id,
            @NotBlank(message = "请输入特征值") @Pattern(regexp = "^[A-Za-z0-9]{1,4}$", message = "特征值为 1～4 位字母、数字") String code,
            @NotBlank(message = "请输入特征值说明") @Size(max = 64, message = "说明最多 64 个字") String name,
            @Pattern(regexp = "^(ENABLED|DISABLED)?$") String status,
            @Size(max = 256) String remark) {
    }

    public record SegmentSave(
            Long id,
            @NotBlank(message = "请输入编码段名称") @Size(max = 32, message = "编码段名称最多 32 个字") String name,
            @NotNull(message = "请输入编码段位数") @Min(value = 1, message = "编码段位数为 1～4") @Max(value = 4, message = "编码段位数为 1～4") Integer length,
            @Size(max = 256) String remark,
            @Valid List<SegmentValueSave> values) {
    }

    public record CodeSchemeSave(@Valid List<SegmentSave> segments) {
    }
}
