package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 计量单位 VO */
public final class UomVOs {

    private UomVOs() {
    }

    public record UomResp(Long id, String code, String name, String nameEn, String category, int precision, int sort,
                          boolean builtin, String status, Integer version) {
    }

    public record UomSave(
            @NotBlank(message = "请输入编码") @Pattern(regexp = "[A-Za-z0-9]{1,16}", message = "编码为 1～16 位字母数字") String code,
            @NotBlank(message = "请输入名称") @Size(max = 16) String name,
            @Size(max = 32) String nameEn,
            @NotBlank(message = "请选择类别") String category,
            @NotNull @Min(value = 0, message = "精度必须在 0～4 之间") @Max(value = 4, message = "精度必须在 0～4 之间") Integer precision,
            @NotNull Integer sort,
            Integer version) {
    }

    public record UomSimple(String code, String name, String nameEn, String category, int precision) {
    }

    public record ConversionResp(Long id, String fromUom, String toUom, BigDecimal rate, Integer version) {
    }

    public record ConversionSave(@NotBlank String fromUom, @NotBlank String toUom,
                                 @NotNull @DecimalMin(value = "0", inclusive = false, message = "换算率必须大于 0") BigDecimal rate,
                                 Integer version) {
    }
}
