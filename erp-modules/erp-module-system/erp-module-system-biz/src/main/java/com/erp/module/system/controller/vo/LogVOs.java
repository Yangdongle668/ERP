package com.erp.module.system.controller.vo;

import com.erp.common.result.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 日志审计 VO */
public final class LogVOs {

    private LogVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LoginLogQuery extends PageParam {
        private String username;
        private String result;
        private String ip;
        private LocalDateTime timeFrom;
        private LocalDateTime timeTo;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OperLogQuery extends PageParam {
        private Long userId;
        private String moduleCode;
        private String action;
        private String result;
        private String traceId;
        private LocalDateTime timeFrom;
        private LocalDateTime timeTo;
    }

    public record LoginLogResp(Long id, LocalDateTime createdAt, String username, String realName, String type, String result,
                               String ip, String browser, String os) {
    }

    public record OperLogResp(Long id, LocalDateTime createdAt, Long userId, String username, String realName, String moduleCode,
                              String moduleName, String action, String method, String path, String result, Integer errorCode,
                              String errorMsg, int durationMs, String ip, String traceId, String params) {
    }

    public record DocLogResp(Long id, String action, String actionName, String fromStatus, String toStatus, String reason,
                             String operatorName, LocalDateTime createdAt) {
    }
}
