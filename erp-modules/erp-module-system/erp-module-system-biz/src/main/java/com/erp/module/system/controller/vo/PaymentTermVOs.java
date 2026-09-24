package com.erp.module.system.controller.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 付款条件 VO */
public final class PaymentTermVOs {

    private PaymentTermVOs() {
    }

    public record TermResp(Long id, String code, String name, String nameEn, String settlementMethod, String usage, String status,
                           String remark, List<NodeVO> nodes, String nodeSummary, Integer version) {
    }

    /** @param percent 比例（小数，0.3 表示 30%） */
    public record NodeVO(
            @NotBlank(message = "请输入节点名称") @Size(max = 32) String name,
            @NotNull(message = "请输入比例") @DecimalMin(value = "0", inclusive = false, message = "比例必须大于 0") @DecimalMax(value = "1", message = "比例不能超过 100%") BigDecimal percent,
            @NotBlank(message = "请选择起算事件") String baseEvent,
            @NotNull(message = "请输入天数") @Min(value = 0, message = "天数必须在 0～365 之间") @Max(value = 365, message = "天数必须在 0～365 之间") Integer days) {
    }

    public record TermSave(
            @NotBlank(message = "请输入编码") @Pattern(regexp = "[A-Za-z0-9_-]{1,32}", message = "编码为 1～32 位字母、数字、- _") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @Size(max = 128) String nameEn,
            @NotBlank(message = "请选择结算方式") String settlementMethod,
            @NotBlank(message = "请选择适用范围") String usage,
            @Size(max = 256) String remark,
            @NotEmpty(message = "请至少添加一个付款节点") List<@Valid NodeVO> nodes,
            Integer version) {
    }

    public record TermSimple(Long id, String code, String name, String nameEn, String usage) {
    }

    public record Country(String code, String nameCn, String nameEn) {
    }
}
