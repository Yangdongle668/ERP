package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.Tracking;
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
                               int version, List<CategoryNode> children) {
    }

    /** 选择器节点；leaf 为末级（物料只能挂在末级） */
    public record SimpleNode(Long id, Long parentId, String code, String name, String codePrefix, MaterialType defaultMaterialType,
                             String defaultBaseUom, Tracking defaultTracking, boolean defaultIqcRequired, Integer defaultShelfLifeDays,
                             boolean leaf, List<SimpleNode> children) {
    }

    public record CategorySave(
            Long parentId,
            @NotBlank(message = "请输入类别编码") @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "类别编码为 1～16 位字母、数字") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @NotBlank(message = "请输入编码前缀") @Pattern(regexp = "^[A-Za-z0-9]{1,8}$", message = "编码前缀为 1～8 位字母、数字") String codePrefix,
            @NotNull(message = "请选择默认物料类型") MaterialType defaultMaterialType,
            @Size(max = 16) String defaultBaseUom,
            @NotNull(message = "请选择默认库存管理方式") Tracking defaultTracking,
            @NotNull Boolean defaultIqcRequired,
            @Min(value = 1, message = "保质期为 1～3650 天") @Max(value = 3650, message = "保质期为 1～3650 天") Integer defaultShelfLifeDays,
            @NotNull Integer sort,
            @Size(max = 256) String remark,
            Integer version) {
    }
}
