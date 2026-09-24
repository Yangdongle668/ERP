package com.erp.module.system.api.log;

/**
 * 单据操作日志（01-11 日志审计）：业务模块在状态变更时调用，与业务操作同一事务写入。
 * 操作人取当前登录用户。
 */
public interface DocLogApi {

    /**
     * @param action     动作编码（DocAction 名称或模块自定义），如 SUBMIT、APPROVE、CLOSE
     * @param actionName 动作名称，如“审核”
     * @param reason     原因（作废、关闭、反审核、驳回时由业务模块校验必填）
     */
    void record(String bizType, Long bizId, String bizNo, String action, String actionName,
                String fromStatus, String toStatus, String reason);
}
