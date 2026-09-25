package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** ECN 工程变更（需求 05-05） */
public final class EcnVOs {

    private EcnVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EcnQuery extends PageParam {
        private String docNo;
        private String title;
        private String ecnType;
        /** 通用状态，逗号分隔；为空默认非关闭、非作废 */
        private String statuses;
        /** 涉及物料：父件、原子件或新子件 */
        private Long materialId;
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }

    public record EcnRow(Long id, String docNo, String title, String ecnType, String reasonType, String urgency, String effectiveMode,
                         LocalDate effectiveDate, String status, String createdByName, LocalDate docDate, LocalDateTime createdAt) {
    }

    /** 变更明细：原用量、原损耗由系统从 BOM 带出 */
    public record LineSave(@NotNull(message = "请选择 BOM") Long bomId, @NotBlank(message = "请选择变更动作") String action, Long oldComponentId,
                           Long newComponentId, BigDecimal newQtyPer, BigDecimal newScrapRate, @Size(max = 1024) String positionNo,
                           @Size(max = 256) String remark) {
    }

    /** 影响分析的处理方式（按 id 更新） */
    public record ImpactSave(@NotNull Long id, String handling, @Size(max = 256) String handlingRemark) {
    }

    /** 执行确认任务（整体替换） */
    public record TaskSave(@NotBlank(message = "请选择执行部门") String deptRole, Long assigneeId,
                           @NotBlank(message = "请填写执行内容") @Size(max = 512) String content) {
    }

    /** impacts / tasks 为 null 时不修改 */
    public record EcnSave(@NotBlank(message = "请填写变更标题") @Size(max = 128) String title,
                          @NotBlank(message = "请选择变更类型") String ecnType,
                          @NotBlank(message = "请选择变更原因") String reasonType,
                          @NotBlank(message = "请填写变更原因说明") @Size(max = 1000) String reason,
                          String urgency, String effectiveMode, LocalDate effectiveDate, Long customerId,
                          @Valid List<LineSave> lines, @Valid List<ImpactSave> impacts, @Valid List<TaskSave> tasks,
                          List<Long> fileIds, Integer version) {
    }

    public record LineResp(Long id, Integer lineNo, Long bomId, String bomNo, Long parentId, String parentCode, String parentName, String action,
                           Long oldComponentId, String oldCode, String oldName, Long newComponentId, String newCode, String newName,
                           String uom, BigDecimal oldQtyPer, BigDecimal newQtyPer, BigDecimal oldScrapRate, BigDecimal newScrapRate,
                           String positionNo, Long newBomId, String newBomNo, String remark) {
    }

    public record ImpactResp(Long id, Long materialId, String materialCode, String materialName, String uom, String impactType, String docNo,
                             BigDecimal qty, String handling, String handlingRemark) {
    }

    public record TaskResp(Long id, String deptRole, Long assigneeId, String assigneeName, String content, String taskStatus, String doneRemark,
                           String doneByName, LocalDateTime doneAt, boolean mine) {
    }

    public record EcnDetail(Long id, String docNo, LocalDate docDate, String title, String ecnType, String reasonType, String reason, String urgency,
                            String effectiveMode, LocalDate effectiveDate, Long customerId, String customerName, boolean analyzed, boolean keyPart,
                            String status, LocalDateTime approvedAt, LocalDateTime effectedAt, LocalDateTime closedAt, String createdByName,
                            LocalDateTime createdAt, int version, List<LineResp> lines, List<ImpactResp> impacts, List<TaskResp> tasks,
                            int pendingTasks) {
    }

    /** 批量替换：原子件 → 新子件；newQtyPer 为空时沿用原用量 */
    public record BatchReplaceReq(@NotNull(message = "请选择原子件") Long oldComponentId, @NotNull(message = "请选择新子件") Long newComponentId,
                                  BigDecimal newQtyPer) {
    }

    /** 提交结果：keyPartWarning 为 R08 的提示 */
    public record SubmitResult(String status, String keyPartWarning) {
    }

    public record DoneReq(@Size(max = 512) String remark) {
    }
}
