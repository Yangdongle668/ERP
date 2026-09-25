package com.erp.module.crm.controller.vo;

import com.erp.common.result.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 跟进记录（需求 03-04） */
public final class FollowupVOs {

    private FollowupVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FollowupQuery extends PageParam {
        private Long customerId;
        private Long ownerId;
        private String followupType;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Long opportunityId;
        /** 仅看待跟进：下次跟进日期 ≤ 今天 + 7 天且之后没有新跟进 */
        private Boolean pendingOnly;
    }

    /** editable：当前用户是记录人且在 24 小时内；nextDue：下次跟进日期已到 */
    public record FollowupRow(Long id, Long customerId, String customerCode, String customerShortName, Long contactId, String contactName,
                              Long opportunityId, String opportunityName, String followupType, LocalDateTime followupAt, String subject, String content,
                              LocalDate nextFollowupAt, String nextPlan, boolean nextDue, Long ownerId, String ownerName, int fileCount,
                              boolean editable, int version) {
    }

    public record FollowupSave(@NotNull(message = "请选择客户") Long customerId, Long contactId, Long opportunityId,
                               @NotBlank(message = "请选择跟进方式") String followupType,
                               @NotNull(message = "请选择跟进时间") LocalDateTime followupAt,
                               @NotBlank(message = "请填写主题") @Size(max = 128) String subject,
                               @NotBlank(message = "请填写内容") @Size(max = 4000) String content,
                               LocalDate nextFollowupAt, @Size(max = 512) String nextPlan, List<Long> fileIds, Integer version) {
    }
}
