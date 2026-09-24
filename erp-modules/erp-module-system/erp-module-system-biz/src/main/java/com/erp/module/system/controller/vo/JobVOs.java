package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** 定时任务、后台任务 VO */
public final class JobVOs {

    private JobVOs() {
    }

    public record JobResp(String code, String name, String moduleCode, String moduleName, String cron, String defaultCron, boolean enabled,
                          LocalDateTime lastRunAt, String lastResult, String lastMessage, LocalDateTime nextRunAt, boolean running) {
    }

    public record CronReq(@NotBlank(message = "请输入 Cron 表达式") String cron) {
    }

    public record JobLogResp(Long id, String jobCode, LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs, String result,
                             String message, String triggerType, String operatorName) {
    }

    public record TaskResp(Long id, String taskType, String name, String moduleCode, String status, int progress, Long resultFileId,
                           boolean resultExpired, String resultMessage, String errorMessage, Long submittedBy, String submittedByName,
                           LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt) {
    }
}
