package com.erp.module.system.api.workflow;

/**
 * 发起审批的结果。
 *
 * @param status     NOT_REQUIRED：该单据类型未配置或未启用审批，调用方直接执行审核逻辑；STARTED：已进入审批
 * @param instanceId 审批实例 ID（STARTED 时）
 */
public record StartResult(Status status, Long instanceId) {

    public enum Status { NOT_REQUIRED, STARTED }

    public static StartResult notRequired() {
        return new StartResult(Status.NOT_REQUIRED, null);
    }

    public boolean isStarted() {
        return status == Status.STARTED;
    }
}
