package com.erp.module.quality.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 客诉 */
public final class ComplaintVOs {

    private ComplaintVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ComplaintQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private Long materialId;
        private String complaintType;
        private String severity;
        /** 逗号分隔；OPEN = 未结案（新建、分析中、已回复、结案审批中） */
        private String statuses;
        private Long qeId;
        private LocalDate receivedFrom;
        private LocalDate receivedTo;
    }

    public record ComplaintRow(Long id, String docNo, Long customerId, String customerName, Long materialId, String materialCode, String materialName,
                               String complaintType, String severity, BigDecimal complaintQty, LocalDateTime receivedAt, LocalDate replyDueDate,
                               boolean replyOverdue, LocalDateTime repliedAt, Long qeId, String qeName, String handling, String status,
                               String salesOwnerName, LocalDateTime createdAt) {
    }

    public record ComplaintSave(@NotNull(message = "请选择客户") Long customerId, Long contactId, @NotBlank(message = "请选择客诉类型") String complaintType,
                                @NotBlank(message = "请选择严重度") String severity, Long materialId, @Size(max = 64) String customerPartNo,
                                @Size(max = 64) String orderNo, @Size(max = 64) String shipmentNo, @Size(max = 64) String batchNo, String serialNos,
                                BigDecimal complaintQty, @NotBlank(message = "请填写问题描述") @Size(max = 20000) String description,
                                @NotNull(message = "请填写收到时间") LocalDateTime receivedAt, LocalDate replyDueDate,
                                @NotNull(message = "请选择负责 QE") Long qeId, List<Long> fileIds, Integer version) {
    }

    public record ComplaintDetail(Long id, String docNo, Long customerId, String customerName, Long contactId, String contactName, String complaintType,
                                  String severity, Long materialId, String materialCode, String materialName, String customerPartNo, String orderNo,
                                  String shipmentNo, String batchNo, String serialNos, BigDecimal complaintQty, String description,
                                  LocalDateTime receivedAt, LocalDate replyDueDate, boolean replyOverdue, Long qeId, String qeName, Long salesOwnerId,
                                  String salesOwnerName, String rootCause, String replyContent, LocalDateTime repliedAt, String handling,
                                  String handlingRemark, BigDecimal claimAmount, BigDecimal agreedAmount, String currency, String status,
                                  Long capaId, String capaNo, String capaStatus, Long ncrId, String ncrNo, String ncrStatus, Long returnId,
                                  List<String> closeMissing, boolean approvalRunning, String cancelReason, LocalDateTime closedAt, LocalDateTime createdAt,
                                  int version) {
    }

    public record ReplyReq(@NotBlank(message = "请填写回复内容") @Size(max = 20000) String content, LocalDateTime repliedAt, @Size(max = 20000) String rootCause,
                           List<Long> fileIds) {
    }

    public record HandlingReq(@NotBlank(message = "请选择处理方式") String handling, BigDecimal claimAmount, BigDecimal agreedAmount, String currency,
                              @Size(max = 512) String remark, Long returnId) {
    }

    public record NcrReq(BigDecimal qty, String responsibility) {
    }

    public record ContactOption(Long id, String name, String phone) {
    }
}
