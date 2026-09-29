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

/** CAPA / 8D */
public final class CapaVOs {

    private CapaVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapaQuery extends PageParam {
        private String docNo;
        private String source;
        private Long leaderId;
        /** OPEN / VERIFYING / CLOSED / CANCELED，逗号分隔 */
        private String statuses;
        private LocalDate dueFrom;
        private LocalDate dueTo;
        private Boolean overdue;
    }

    public record CapaRow(Long id, String docNo, String title, String source, Long sourceId, String sourceNo, Long materialId, String materialCode,
                         Long leaderId, String leaderName, int currentStep, LocalDate dueDate, boolean overdue, LocalDate d3Due, String status,
                         int invalidCount, LocalDateTime createdAt) {
    }

    public record CapaSave(@NotBlank(message = "请填写标题") @Size(max = 128) String title, String source, Long sourceId, @Size(max = 64) String sourceNo,
                           Long materialId, Long customerId, Long supplierId, @NotNull(message = "请选择负责人") Long leaderId, List<Long> teamMembers,
                           @NotNull(message = "请填写完成期限") LocalDate dueDate, Integer version) {
    }

    public record Member(Long id, String name) {
    }

    public record CapaDetail(Long id, String docNo, String title, String source, Long sourceId, String sourceNo, Long materialId, String materialCode,
                             String materialName, Long customerId, String customerName, Long supplierId, String supplierName, Long leaderId,
                             String leaderName, List<Member> teamMembers, String d1Team, String d2Problem, String d3Containment, LocalDate d3Due,
                             LocalDateTime d3DoneAt, String d4RootCause, String d4Method, String d5Actions, String d6Implementation,
                             String d7Prevention, String d8Summary, int currentStep, LocalDate dueDate, boolean overdue, String verifyResult,
                             Long verifyBy, String verifyName, LocalDateTime verifyAt, int invalidCount, String verifyHistory, String status,
                             boolean canEdit, LocalDateTime closedAt, String cancelReason, int version) {
    }

    /** 步骤内容：D1 为小组说明（可同时改成员），D4 可填分析方法，D3 可改期限 */
    public record StepSave(@Size(max = 20000) String content, @Size(max = 64) String method, List<Long> teamMembers, LocalDate d3Due) {
    }

    public record VerifyReq(@NotBlank(message = "请选择验证结果") String result, @NotBlank(message = "请填写验证数据") @Size(max = 20000) String content) {
    }
}
