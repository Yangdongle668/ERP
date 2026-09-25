package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 工装台账（需求 05-08） */
public final class ToolingVOs {

    private ToolingVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ToolingQuery extends PageParam {
        private String code;
        private String name;
        private String toolingType;
        private String ownership;
        private String toolingStatus;
        private Long materialId;
        /** 仅显示使用率 ≥ 预警比例 */
        private Boolean lifeWarning;
    }

    /**
     * @param lifePct       使用次数 / 设计寿命（0～1+），不管控寿命时为空
     * @param toMaintain    距下次保养（次），不保养时为空
     */
    public record ToolingRow(Long id, String code, String name, String toolingType, String spec, String ownership, Long customerId,
                             String customerName, int cavity, Integer designLife, int usedCount, BigDecimal lifePct, boolean lifeWarn,
                             Integer maintainCycle, int lastMaintainCount, Integer toMaintain, String location, String toolingStatus,
                             Long holderId, String holderName, String supplierName, LocalDate purchaseDate, BigDecimal purchaseAmount,
                             boolean allowOverLife, String overLifeReason, List<Long> materialIds, List<CertVOs.MaterialRef> materials,
                             String remark, int version) {
    }

    public record ToolingSave(@Size(max = 32) String code, @NotBlank(message = "请输入名称") @Size(max = 128) String name,
                              @NotBlank(message = "请选择工装类型") String toolingType, @Size(max = 256) String spec,
                              String ownership, Long customerId, @Min(value = 1, message = "模穴数至少为 1") Integer cavity,
                              @Min(value = 1, message = "设计寿命必须大于 0") Integer designLife,
                              @Min(value = 0, message = "初始使用次数不能小于 0") Integer initialUsedCount,
                              @Min(value = 1, message = "保养周期必须大于 0") Integer maintainCycle, @Size(max = 64) String location,
                              @Size(max = 128) String supplierName, LocalDate purchaseDate,
                              @DecimalMin(value = "0", message = "金额不能小于 0") BigDecimal purchaseAmount,
                              Boolean allowOverLife, @Size(max = 256) String overLifeReason, List<Long> materialIds,
                              @Size(max = 512) String remark, Integer version) {
    }

    /**
     * 登记（借出、归还、保养、送修、修复、报废、次数调整）。
     *
     * @param userId 借出：借用人；其他：经办人（为空取当前用户）
     * @param count  次数调整：调整后次数
     */
    public record RecordReq(Long userId, @Size(max = 512) String content, @Size(max = 128) String vendor,
                            @DecimalMin(value = "0", message = "费用不能小于 0") BigDecimal cost, LocalDate date, LocalDate expectedReturn,
                            @Min(value = 0, message = "次数不能小于 0") Integer count) {
    }

    public record RecordRow(Long id, String recordType, Integer count, Long userId, String userName, String sourceDocNo, String content,
                            BigDecimal cost, LocalDateTime occurredAt, String createdByName) {
    }
}
