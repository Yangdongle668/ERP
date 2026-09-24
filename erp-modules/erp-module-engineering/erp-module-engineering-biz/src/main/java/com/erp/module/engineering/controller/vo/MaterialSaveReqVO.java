package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建/修改物料。
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
        Long categoryId,
        @NotBlank(message = "基本单位不能为空") @Size(max = 16) String baseUom,
        @Size(max = 512) String remark,
        Integer version) {
}
