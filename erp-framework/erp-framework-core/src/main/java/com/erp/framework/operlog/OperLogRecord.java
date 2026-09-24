package com.erp.framework.operlog;

import java.time.LocalDateTime;

/**
 * 一条操作日志。
 *
 * @param result SUCCESS / BIZ_ERROR / SYSTEM_ERROR
 */
public record OperLogRecord(String traceId, String moduleCode, String action, String method, String path, String params,
                            String result, Integer errorCode, String errorMsg, int durationMs,
                            Long userId, String username, String realName, String ip, LocalDateTime createdAt) {
}
