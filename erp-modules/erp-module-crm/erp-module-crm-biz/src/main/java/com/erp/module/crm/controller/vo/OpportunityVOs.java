package com.erp.module.crm.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 商机（需求 03-05） */
public final class OpportunityVOs {

    private OpportunityVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OppQuery extends PageParam {
        private String keyword;
        private Long customerId;
        private Long ownerId;
        private String stage;
        /** 逗号分隔；为空默认进行中 */
        private String statuses;
        private LocalDate expectedFrom;
        private LocalDate expectedTo;
    }

    /** overdue：预计成交日期已过仍在进行中 */
    public record OppRow(Long id, String code, String name, Long customerId, String customerCode, String customerShortName, Long contactId,
                         String contactName, String stage, String status, BigDecimal amount, String currency, BigDecimal winRate, LocalDate expectedDate,
                         boolean overdue, String products, String competitor, Long ownerId, String ownerName, LocalDateTime lastFollowupAt,
                         String lostReason, String lostRemark, String wonOrderNo, String remark, LocalDateTime closedAt, int version) {
    }

    public record OppSave(@NotBlank(message = "请填写商机名称") @Size(max = 128) String name, @NotNull(message = "请选择客户") Long customerId,
                          Long contactId, String stage,
                          @NotNull(message = "请填写预计金额") @DecimalMin(value = "0", message = "金额不能小于 0") BigDecimal amount, String currency,
                          @DecimalMin(value = "0", message = "赢率为 0～100%") @DecimalMax(value = "1", message = "赢率为 0～100%") BigDecimal winRate,
                          @NotNull(message = "请选择预计成交日期") LocalDate expectedDate, @Size(max = 512) String products,
                          @Size(max = 256) String competitor, Long ownerId, @Size(max = 1000) String remark, Integer version) {
    }

    public record StageReq(@NotBlank(message = "请选择阶段") String stage) {
    }

    public record WinReq(@Size(max = 64) String orderNo) {
    }

    public record LoseReq(String lostReason, @Size(max = 512) String remark) {
    }

    public record RemarkReq(@Size(max = 512) String remark) {
    }

    /** 漏斗：各阶段进行中商机的数量、金额（本位币）、加权金额 */
    public record FunnelStage(String stage, int count, BigDecimal amountBase, BigDecimal weightedBase) {
    }

    public record Funnel(String baseCurrency, List<FunnelStage> stages, int totalCount, BigDecimal totalAmountBase, BigDecimal totalWeightedBase) {
    }
}
