package com.erp.module.finance.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 财务基础设置（12-01） */
public final class SettingVOs {

    private SettingVOs() {
    }

    // ==================== 会计科目 ====================

    public record AccountNode(Long id, String code, String name, String parentCode, String accountType, String direction, List<String> auxTypes,
                              boolean currencyAccounting, boolean leaf, int level, String status, List<AccountNode> children) {
    }

    public record AccountSave(@NotBlank String code, @NotBlank String name, String parentCode, String accountType, String direction,
                              List<String> auxTypes, Boolean currencyAccounting) {
    }

    /** 下拉：启用的末级科目 */
    public record AccountOption(String code, String name, String fullName, List<String> auxTypes) {
    }

    // ==================== 会计期间 ====================

    public record PeriodVO(Long id, String period, LocalDate startDate, LocalDate endDate, String status, boolean costLocked,
                           String closedByName, LocalDateTime closedAt) {
    }

    public record InitYearReq(@NotNull Integer year, String openFrom) {
    }

    // ==================== 银行账户 ====================

    public record BankAccountVO(Long id, String code, String name, String bankName, String accountNo, String currency, String swift,
                                String bankAddress, String accountCode, boolean isDefault, String status, String remark) {
    }

    public record BankAccountSave(@NotBlank String code, @NotBlank String name, @NotBlank String bankName, @NotBlank String accountNo,
                                  @NotBlank String currency, String swift, String bankAddress, String accountCode, Boolean isDefault,
                                  String status, String remark) {
    }

    public record BankOption(Long id, String code, String name, String currency, String accountNo, boolean isDefault) {
    }

    // ==================== 科目映射 ====================

    /**
     * 分录模板
     *
     * @param direction     DEBIT / CREDIT
     * @param amountField   amount（不含税）/ tax / totalAmount / cost / fee / fxDiff
     * @param auxFrom       CUSTOMER / SUPPLIER / DEPT / MATERIAL（取单据上的对应对象）
     */
    public record MappingEntry(String direction, String accountCode, String amountField, String summaryTemplate, String auxFrom) {
    }

    public record MappingVO(Long id, String bizType, String matchCondition, String conditionDesc, int priority, List<MappingEntry> entries,
                            String status, String remark) {
    }

    public record MappingSave(@NotBlank String bizType, String matchCondition, String conditionDesc, Integer priority,
                              List<MappingEntry> entries, String status, String remark) {
    }
}
