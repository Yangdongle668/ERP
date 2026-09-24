package com.erp.module.system.api.workflow;

import com.erp.common.event.DomainEvent;

/**
 * 审批实例结束时在审批动作的同一事务内发布。业务模块用 {@code @EventListener} 同步处理：
 * APPROVED → 执行审核逻辑；其他结果 → 单据回到草稿。业务处理抛出异常时审批动作整体回滚。
 */
public class ApprovalCompletedEvent extends DomainEvent {

    public enum Result { APPROVED, REJECTED, WITHDRAWN, TERMINATED }

    private final String bizType;
    private final Long bizId;
    private final Long instanceId;
    private final Result result;
    private final String comment;
    private final Long operatorId;

    public ApprovalCompletedEvent(String bizType, Long bizId, Long instanceId, Result result, String comment, Long operatorId) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.instanceId = instanceId;
        this.result = result;
        this.comment = comment;
        this.operatorId = operatorId;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public Result getResult() {
        return result;
    }

    public String getComment() {
        return comment;
    }

    public Long getOperatorId() {
        return operatorId;
    }
}
