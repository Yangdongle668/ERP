package com.erp.module.crm.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户信用（需求 03-03） */
public final class CreditVOs {

    private CreditVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CreditQuery extends PageParam {
        private Long customerId;
        private Long ownerId;
        /** 使用率：80 表示 ≥80%，100 表示 ≥100% */
        private Integer usageAtLeast;
        private Boolean overdueOnly;
    }

    /**
     * @param control      客户的控制方式（DEFAULT 时 effectiveControl 为系统参数）
     * @param usagePct     使用率（0.85 = 85%），未设置额度时为空
     * @param pendingChangeId 未完成的调整单
     */
    public record CreditRow(Long customerId, String customerCode, String customerShortName, Long ownerId, String ownerName, String control,
                            String effectiveControl, BigDecimal creditLimit, BigDecimal receivableBalance, BigDecimal overdueAmount,
                            BigDecimal openOrderAmount, BigDecimal used, BigDecimal available, BigDecimal usagePct, Integer creditDays,
                            LocalDateTime refreshedAt, Long pendingChangeId, String pendingChangeNo) {
    }

    /** expireDate 不为空表示临时额度：到期后自动恢复原额度 */
    public record ChangeSave(@NotNull(message = "请选择客户") Long customerId,
                             @NotNull(message = "请填写新额度") @DecimalMin(value = "0", message = "额度不能小于 0") BigDecimal newLimit,
                             Integer newDays, String newControl, LocalDate expireDate,
                             @NotBlank(message = "请填写调整原因") @Size(max = 512) String reason) {
    }

    public record ChangeRow(Long id, String docNo, LocalDate docDate, Long customerId, String customerCode, String customerShortName,
                            BigDecimal oldLimit, BigDecimal newLimit, Integer oldDays, Integer newDays, String oldControl, String newControl,
                            LocalDate expireDate, boolean restored, String reason, String status, String createdByName, LocalDateTime createdAt,
                            LocalDateTime approvedAt) {
    }
}
