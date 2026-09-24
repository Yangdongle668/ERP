package com.erp.module.system.api.workflow;

/**
 * 发起审批的结果。
 *
 * @param status     NOT_REQUIRED：该单据类型未配置或未启用审批，调用方直接执行审核逻辑；
 *                   AUTO_APPROVED：已按流程发起，但所有节点都自动通过（如审批人都是发起人本人），调用方同样直接执行审核逻辑，
 *                   审批记录中保留自动通过的明细；STARTED：已进入审批，单据置为“待审批”，结果以 ApprovalCompletedEvent 通知
 * @param instanceId 审批实例 ID（AUTO_APPROVED、STARTED 时）
 */
public record StartResult(Status status, Long instanceId) {

    public enum Status { NOT_REQUIRED, AUTO_APPROVED, STARTED }

    public static StartResult notRequired() {
        return new StartResult(Status.NOT_REQUIRED, null);
    }

    /** true：单据进入“待审批”；false：调用方应直接执行审核逻辑 */
    public boolean isStarted() {
        return status == Status.STARTED;
    }
}
