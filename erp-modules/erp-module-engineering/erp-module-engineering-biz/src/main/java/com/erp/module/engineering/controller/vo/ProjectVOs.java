package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 研发项目（需求 05-06） */
public final class ProjectVOs {

    private ProjectVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ProjectQuery extends PageParam {
        private String docNo;
        private String name;
        private Long customerId;
        private Long pmUserId;
        private String stage;
        /** 逗号分隔；为空默认非完成、非取消 */
        private String statuses;
        private LocalDate planEndFrom;
        private LocalDate planEndTo;
    }

    public record ProjectRow(Long id, String docNo, String name, String projectType, Long customerId, String customerName, Long productMaterialId,
                             String productCode, String productName, Long pmUserId, String pmName, String stage, BigDecimal progressPct,
                             LocalDate planStart, LocalDate planEnd, boolean overdue, String priority, String projectStatus) {
    }

    public record ProjectSave(@NotBlank(message = "请输入项目名称") @Size(max = 128) String name,
                              @NotBlank(message = "请选择项目类型") String projectType, Long customerId, Long productMaterialId,
                              @NotNull(message = "请选择项目经理") Long pmUserId, String priority,
                              @NotNull(message = "请选择计划开始日期") LocalDate planStart, @NotNull(message = "请选择计划结束日期") LocalDate planEnd,
                              String description, @Valid List<MemberSave> members, Integer version) {
    }

    public record MemberSave(@NotNull(message = "请选择成员") Long userId, @Size(max = 32) String role) {
    }

    public record MemberRow(Long userId, String name, String deptName, String role) {
    }

    /** canUpdate：当前用户是否为任务负责人或项目经理（可以更新状态） */
    public record TaskRow(Long id, String stage, String name, Long ownerId, String ownerName, LocalDate planStart, LocalDate planEnd,
                          LocalDate actualEnd, String taskStatus, String deliverable, int weight, String remark, boolean overdue, int fileCount,
                          boolean canUpdate) {
    }

    /** 关联：样品单（按项目）、BOM、ECN、认证（按产品物料） */
    public record RelatedRow(String docType, Long id, String docNo, String title, String status) {
    }

    /**
     * @param undoneInStage 当前阶段未完成（非 DONE/CANCELED）的任务数（推进阶段时提示）
     * @param canManage     当前用户可以编辑项目、新建任务（项目经理或有 eng:project:update）
     */
    public record ProjectDetail(Long id, String docNo, String name, String projectType, Long customerId, String customerName, Long productMaterialId,
                                String productCode, String productName, Long pmUserId, String pmName, String stage, BigDecimal progressPct,
                                LocalDate planStart, LocalDate planEnd, LocalDate actualStart, LocalDate actualEnd, String priority, String projectStatus,
                                String description, String cancelReason, List<MemberRow> members, List<TaskRow> tasks, List<RelatedRow> related,
                                int undoneInStage, int undoneTotal, boolean canManage, String createdByName, LocalDateTime createdAt, int version) {
    }

    public record TaskSave(String stage, @NotBlank(message = "请输入任务名称") @Size(max = 128) String name,
                           @NotNull(message = "请选择负责人") Long ownerId,
                           @NotNull(message = "请选择计划开始日期") LocalDate planStart, @NotNull(message = "请选择计划结束日期") LocalDate planEnd,
                           @Size(max = 256) String deliverable, @Min(value = 1, message = "权重为 1～10") @Max(value = 10, message = "权重为 1～10") Integer weight,
                           @Size(max = 512) String remark) {
    }

    public record TaskStatusReq(@NotBlank(message = "请选择状态") String status, @Size(max = 256) String deliverable) {
    }

    public record StageReq(@NotBlank(message = "请选择阶段") String stage) {
    }

    public record ReasonReq(@Size(max = 256) String reason) {
    }
}
