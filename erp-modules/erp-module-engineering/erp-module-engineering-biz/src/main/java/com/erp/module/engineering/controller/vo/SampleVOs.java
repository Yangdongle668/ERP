package com.erp.module.engineering.controller.vo;

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
import java.util.List;

/** 样品单（需求 05-07） */
public final class SampleVOs {

    private SampleVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SampleQuery extends PageParam {
        private String docNo;
        private Long customerId;
        private Long materialId;
        private String sampleType;
        /** 逗号分隔；为空默认未关闭未作废 */
        private String statuses;
        private LocalDate requiredFrom;
        private LocalDate requiredTo;
        private Long createdBy;
        private Long projectId;
    }

    /** overdue：要求日期已过仍未寄出 */
    public record SampleRow(Long id, String docNo, String sampleType, Long customerId, String customerName, Long materialId, String materialCode,
                            String materialName, String uom, BigDecimal qty, LocalDate requiredDate, boolean overdue, String makeMethod,
                            String sampleStatus, String feedbackResult, String createdByName, LocalDateTime createdAt) {
    }

    public record SampleSave(@NotBlank(message = "请选择样品类型") String sampleType, Long customerId, Long contactId, Long projectId,
                             @NotNull(message = "请选择物料") Long materialId, @Size(max = 64) String customerPartNo,
                             @NotNull(message = "请输入数量") @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0") BigDecimal qty,
                             @NotNull(message = "请选择要求日期") LocalDate requiredDate, String makeMethod,
                             @NotBlank(message = "请填写用途") @Size(max = 512) String purpose, @Size(max = 1000) String requirements,
                             @Size(max = 512) String shipAddress, List<Long> fileIds, Integer version) {
    }

    public record SampleDetail(Long id, String docNo, String sampleType, Long customerId, String customerName, Long contactId, Long projectId,
                               String projectNo, String projectName, Long materialId, String materialCode, String materialName, String materialSpec,
                               String materialStatus, String uom, String customerPartNo, BigDecimal qty, LocalDate requiredDate, String makeMethod,
                               String purpose, String requirements, String shipAddress, Long prodOrderId, String prodOrderNo, Long stockOutId,
                               String stockOutNo, boolean stockOutDone, LocalDate shipDate, String courier, String trackingNo, String feedbackResult,
                               LocalDate feedbackDate, String feedbackContent, String sampleStatus, String status, String closeReason,
                               boolean productionAvailable, String createdByName, LocalDateTime createdAt, int version) {
    }

    public record ShipReq(@NotNull(message = "请选择寄出日期") LocalDate shipDate, @NotBlank(message = "请填写快递公司") @Size(max = 32) String courier,
                          @NotBlank(message = "请填写快递单号") @Size(max = 64) String trackingNo, @Size(max = 512) String shipAddress) {
    }

    public record FeedbackReq(@NotBlank(message = "请选择反馈结果") String result, @NotNull(message = "请选择反馈日期") LocalDate feedbackDate,
                              @Size(max = 1000) String content, List<Long> fileIds) {
    }

    public record ReasonReq(@Size(max = 256) String reason) {
    }
}
