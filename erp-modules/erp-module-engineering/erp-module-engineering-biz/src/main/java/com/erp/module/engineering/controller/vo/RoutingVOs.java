package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 工作中心、工艺路线接口的请求/响应（需求 05-04） */
public final class RoutingVOs {

    private RoutingVOs() {
    }

    // ==================== 工作中心 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WorkCenterQuery extends PageParam {
        private String keyword;
        private Long deptId;
        private String wcType;
        private String status;
    }

    /** 费率无 eng:work-center:rate 权限时为空，rateVisible = false（前端显示 ***） */
    public record WorkCenterRow(Long id, String code, String name, Long deptId, String deptName, String wcType, BigDecimal hoursPerShift,
                                int shiftCount, BigDecimal efficiencyPct, BigDecimal capacityHoursPerDay, BigDecimal laborRate,
                                BigDecimal overheadRate, boolean rateVisible, String status, String remark, int version) {
    }

    public record WorkCenterSave(
            @NotBlank(message = "请输入编码") @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$", message = "编码为 1～32 位字母、数字、_ -") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64) String name,
            @NotNull(message = "请选择所属车间") Long deptId,
            @NotBlank(message = "请选择类型") String wcType,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "每班小时为 0～24") @DecimalMax(value = "24", message = "每班小时为 0～24") BigDecimal hoursPerShift,
            @NotNull @Min(value = 1, message = "班次为 1～4") @Max(value = 4, message = "班次为 1～4") Integer shiftCount,
            @NotNull @DecimalMin(value = "0.01", message = "效率为 1%～200%") @DecimalMax(value = "2", message = "效率为 1%～200%") BigDecimal efficiencyPct,
            @DecimalMin(value = "0", message = "费率不能小于 0") BigDecimal laborRate,
            @DecimalMin(value = "0", message = "费率不能小于 0") BigDecimal overheadRate,
            @Size(max = 256) String remark,
            Integer version) {
    }

    public record WorkCenterSimple(Long id, String code, String name, String wcType) {
    }

    // ==================== 工艺路线 ====================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RoutingQuery extends PageParam {
        private Long materialId;
        private String keyword;
        private String statuses;
        private Boolean defaultOnly;
        private Long workCenterId;
    }

    public record RoutingRow(Long id, String docNo, Long materialId, String materialCode, String materialName, String materialSpec, int version,
                             boolean isDefault, int stepCount, BigDecimal totalRunSeconds, String description, String status,
                             String updatedByName, LocalDateTime updatedAt) {
    }

    public record RoutingDetail(Long id, String docNo, Long materialId, String materialCode, String materialName, String materialSpec,
                                String materialType, int version, boolean isDefault, String description, String remark, String status,
                                Long copiedFromId, String copiedFromNo, BigDecimal totalSetupMinutes, BigDecimal totalRunSeconds,
                                List<WcSummary> byWorkCenter, String createdByName, LocalDateTime createdAt, String updatedByName,
                                LocalDateTime updatedAt, int rowVersion, List<StepResp> steps) {
    }

    public record WcSummary(Long workCenterId, String workCenterName, BigDecimal setupMinutes, BigDecimal runSeconds) {
    }

    public record StepResp(Long id, int seq, String operation, Long workCenterId, String workCenterCode, String workCenterName, String wcType,
                           BigDecimal setupMinutes, BigDecimal runSeconds, boolean isReportPoint, boolean isInspectionPoint, boolean isOutsourced,
                           String remark) {
    }

    public record RoutingSave(@NotNull(message = "请选择物料") Long materialId, @Size(max = 256) String description, @Size(max = 512) String remark,
                              @Valid List<StepSave> steps, Integer rowVersion) {
    }

    public record StepSave(@NotNull(message = "请填写工序号") @Min(value = 1, message = "工序号必须为正整数") Integer seq,
                           @NotBlank(message = "请选择工序") String operation, @NotNull(message = "请选择工作中心") Long workCenterId,
                           @DecimalMin(value = "0", message = "准备时间不能小于 0") BigDecimal setupMinutes,
                           BigDecimal runSeconds, Boolean isReportPoint, Boolean isInspectionPoint, Boolean isOutsourced,
                           @Size(max = 256) String remark) {
    }

    /** 保存结果：warnings 为不阻止保存的提示（如 BOM 引用了新版本中不存在的工序） */
    public record SaveResult(Long id, List<String> warnings) {
    }
}
