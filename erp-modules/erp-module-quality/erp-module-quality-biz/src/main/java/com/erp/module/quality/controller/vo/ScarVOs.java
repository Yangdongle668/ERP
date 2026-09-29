package com.erp.module.quality.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** SCAR */
public final class ScarVOs {

    private ScarVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ScarQuery extends PageParam {
        private String docNo;
        private Long supplierId;
        private Long materialId;
        /** 逗号分隔；OPEN = 未结案 */
        private String statuses;
        private Boolean overdue;
    }

    public record ScarRow(Long id, String docNo, Long supplierId, String supplierName, Long materialId, String materialCode, String materialName,
                          String problemSummary, LocalDateTime sentAt, LocalDate replyDueDate, boolean replyOverdue, LocalDateTime repliedAt,
                          int invalidCount, String status, String ownerName, Long ncrId, String ncrNo, LocalDateTime createdAt) {
    }

    public record ScarSave(@NotNull(message = "请选择供应商") Long supplierId, Long ncrId, @NotNull(message = "请选择物料") Long materialId,
                           @Size(max = 64) String batchNo, @NotBlank(message = "请填写问题描述") @Size(max = 20000) String problemDescription,
                           @NotBlank(message = "请填写要求") @Size(max = 20000) String requirement, LocalDate replyDueDate, List<Long> fileIds,
                           Integer version) {
    }

    public record ScarDetail(Long id, String docNo, Long supplierId, String supplierName, Long ncrId, String ncrNo, Long materialId, String materialCode,
                             String materialName, String batchNo, String problemDescription, String requirement, LocalDate replyDueDate,
                             boolean replyOverdue, LocalDateTime sentAt, String replyContent, LocalDateTime repliedAt, String verifyPlan,
                             String verifyResult, int invalidCount, String status, Long ownerId, String ownerName, String cancelReason,
                             LocalDateTime closedAt, LocalDateTime createdAt, int version) {
    }

    public record ReplyReq(@NotBlank(message = "请填写供应商回复摘要") @Size(max = 20000) String content, LocalDateTime repliedAt, List<Long> fileIds) {
    }

    public record VerifyPlanReq(@NotBlank(message = "请填写验证方式") @Size(max = 512) String verifyPlan) {
    }

    public record VerifyReq(@NotBlank(message = "请选择验证结果") String result, @Size(max = 512) String remark) {
    }
}
