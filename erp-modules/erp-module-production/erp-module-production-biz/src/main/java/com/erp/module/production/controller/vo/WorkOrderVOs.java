package com.erp.module.production.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 工单派工（需求 09-02） */
public final class WorkOrderVOs {

    private WorkOrderVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WorkOrderQuery extends PageParam {
        private String docNo;
        private Long prodOrderId;
        private String prodOrderNo;
        private Integer operationSeq;
        private Long workCenterId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String shift;
        /** woStatus，逗号分隔 */
        private String statuses;
    }

    /** @param completionRate 完成率 = (合格 + 不良 + 报废) ÷ 派工数量 */
    public record WorkOrderRow(Long id, String docNo, Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName,
                               int operationSeq, String operation, Long workCenterId, String workCenterName, LocalDate planDate, String shift,
                               BigDecimal planQty, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty, BigDecimal completionRate,
                               Long teamLeaderId, String teamLeaderName, String woStatus, String remark) {
    }

    /** 待派工的工序：未派 = 订单数量 × (1 + 超产比例) − 已派 − 未经派工的报工 */
    public record Dispatchable(Long prodOrderId, String prodOrderNo, Long materialId, String materialCode, String materialName, int priority,
                               LocalDate planStart, LocalDate planEnd, Long deptId, int operationSeq, String operation, Long workCenterId,
                               String workCenterName, BigDecimal orderQty, BigDecimal dispatchedQty, BigDecimal undispatchedQty,
                               BigDecimal stdRunSeconds, BigDecimal stdSetupMinutes) {
    }

    public record DispatchLine(@NotNull(message = "请选择日期") LocalDate planDate, @NotNull(message = "请选择班次") String shift,
                               @NotNull(message = "请选择工作中心") Long workCenterId, @NotNull(message = "请填写派工数量") BigDecimal planQty,
                               Long teamLeaderId, @Size(max = 256) String remark) {
    }

    public record DispatchReq(@NotNull Long prodOrderId, @NotNull Integer operationSeq, @NotEmpty(message = "请至少添加一行派工") @Valid List<DispatchLine> lines) {
    }

    /** 派工结果：工单 ID 与产能提示（超出日产能时提示，不阻止） */
    public record DispatchResult(List<Long> ids, List<String> warnings) {
    }

    /** 工作中心某日的负荷：已派工时 / 日产能 */
    public record Load(Long workCenterId, LocalDate date, BigDecimal loadHours, BigDecimal capacityHours) {
    }
}
