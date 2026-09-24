package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 审批流 VO（需求 01-系统管理/08） */
public final class WorkflowVOs {

    private WorkflowVOs() {
    }

    // ==================== 配置 ====================

    /** 单据类型（左侧树）：configStatus = NONE 未配置 / ENABLED 已启用 / DISABLED 已停用 */
    public record BizTypeResp(String bizType, String name, String moduleCode, String moduleName, String configStatus, boolean hasDraft,
                              Integer activeVersion, JsonNode fields, JsonNode userFields) {
    }

    public record Condition(@NotBlank String field, @NotBlank String op, JsonNode value) {
    }

    public record NodeView(Long id, int seq, String name, String approverType, JsonNode approverValue, String multiMode, String approverSummary) {
    }

    public record BranchView(Long id, int priority, String name, boolean isDefault, List<Condition> conditions, List<NodeView> nodes) {
    }

    public record DefinitionView(Long id, String bizType, int defVersion, String status, boolean enabled, boolean skipInitiator,
                                 boolean skipDuplicate, String emptyPolicy, Integer basedOn, LocalDateTime publishedAt,
                                 String publishedByName, String remark, List<BranchView> branches) {
    }

    public record Definitions(DefinitionView active, DefinitionView draft) {
    }

    public record NodeSave(@NotBlank(message = "请输入节点名称") @Size(max = 32, message = "节点名称不能超过 32 字") String name,
                           @NotBlank String approverType, JsonNode approverValue, String multiMode) {
    }

    public record BranchSave(@NotBlank(message = "请输入分支名称") @Size(max = 64, message = "分支名称不能超过 64 字") String name,
                             boolean isDefault, List<@Valid Condition> conditions, List<@Valid NodeSave> nodes) {
    }

    public record DefinitionSave(boolean skipInitiator, boolean skipDuplicate, @NotBlank String emptyPolicy,
                                 @NotNull List<@Valid BranchSave> branches, Integer version) {
    }

    public record PublishReq(@Size(max = 256, message = "版本说明不能超过 256 字") String remark) {
    }

    public record EnabledReq(boolean enabled) {
    }

    public record HistoryResp(Long id, int defVersion, String status, LocalDateTime publishedAt, String publishedByName, String remark) {
    }

    // ==================== 审批 ====================

    public record CommentReq(@Size(max = 500, message = "审批意见不能超过 500 字") String comment) {
    }

    public record TransferReq(@NotNull(message = "请选择转交对象") Long toUserId,
                              @Size(max = 500, message = "说明不能超过 500 字") String comment) {
    }

    public record ReasonReq(@Size(max = 500, message = "原因不能超过 500 字") String reason) {
    }

    public record BatchApproveReq(@NotNull List<Long> taskIds, @Size(max = 500) String comment) {
    }

    public record BatchResult(Long taskId, String bizNo, boolean success, String message) {
    }

    public record TaskView(Long id, int nodeSeq, String nodeName, String multiMode, Long assigneeId, String assigneeName, String status,
                           String comment, String autoReason, String transferToName, String handledByName, LocalDateTime createdAt,
                           LocalDateTime finishedAt) {
    }

    public record InstanceView(Long id, String bizType, Long bizId, String bizNo, String title, String status, Long initiatorId,
                               String initiatorName, LocalDateTime startedAt, LocalDateTime finishedAt, String currentNodeName,
                               String resultComment, JsonNode variables, List<TaskView> tasks) {
    }

    /** 单据的审批记录 + 当前用户可做的动作 */
    public record ByBizResp(List<InstanceView> instances, Long myPendingTaskId, boolean canWithdraw, Long runningInstanceId) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InstanceQuery extends PageParam {
        private String bizType;
        private String bizNo;
        private Long initiatorId;
        private Long assigneeId;
        private String status;
        private LocalDateTime startedFrom;
        private LocalDateTime startedTo;
    }

    public record MonitorResp(Long id, String bizType, String bizTypeName, Long bizId, String bizNo, String title, String initiatorName,
                              LocalDateTime startedAt, String currentNodeName, List<String> assigneeNames, List<PendingTask> pendingTasks,
                              Long stayMinutes, String status, String detailRoute) {
    }

    public record PendingTask(Long taskId, Long assigneeId, String assigneeName) {
    }

    /** 我的待办 / 已处理 */
    public record MyTaskResp(Long taskId, Long instanceId, String bizType, String bizTypeName, Long bizId, String bizNo, String title,
                             String nodeName, String status, String initiatorName, LocalDateTime startedAt, LocalDateTime createdAt,
                             LocalDateTime handledAt, String detailRoute) {
    }

    public record MyInstanceResp(Long id, String bizType, String bizTypeName, Long bizId, String bizNo, String title, String status,
                                 String currentNodeName, LocalDateTime startedAt, LocalDateTime finishedAt, String detailRoute) {
    }
}
