package com.erp.module.crm.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 应用领域（客户编码 LD-领域字母-流水号） */
public final class AppDomainVOs {

    private AppDomainVOs() {
    }

    /** customerCount：使用该领域的客户数；nextCode：下一个自动生成的客户编码 */
    public record AppDomainRow(Long id, String code, String name, String nameEn, Integer sort, String status, String remark,
                               long customerCount, String nextCode, int version) {
    }

    /** 下拉选择：label 为「字母 名称」 */
    public record AppDomainOption(String code, String name, String nameEn, String status) {
    }

    public record AppDomainSave(@NotBlank(message = "请填写领域字母") @Size(max = 1, message = "领域字母为一个英文字母") String code,
                                @NotBlank(message = "请填写领域名称") @Size(max = 64) String name,
                                @Size(max = 128) String nameEn, Integer sort, @Size(max = 256) String remark, Integer version) {
    }
}
