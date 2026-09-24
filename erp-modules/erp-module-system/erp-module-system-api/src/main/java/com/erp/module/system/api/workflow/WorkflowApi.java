package com.erp.module.system.api.workflow;

import java.util.Map;

/**
 * 审批流（需求 01-系统管理/08-审批流）。
 *
 * <p>业务模块在“提交”时于同一事务中调用 {@link #start}；返回 NOT_REQUIRED 时直接审核，
 * 返回 STARTED 时单据置为“待审批”，最终结果以 {@link ApprovalCompletedEvent} 通知。
 */
public interface WorkflowApi {

    /**
     * @param variables 条件字段值（与 ApprovalBizDefinition 声明的字段编码一致）
     * @param bizUsers  单据中的用户字段（用于“单据指定字段”审批人），如 salespersonId → 用户 ID
     */
    StartResult start(String bizType, Long bizId, String bizNo, String title,
                      Map<String, Object> variables, Map<String, Long> bizUsers, Long initiatorId);

    /** 发起人撤回（业务单据的“撤回”按钮调用）。 */
    void withdraw(String bizType, Long bizId, Long operatorId);

    /** 单据当前是否有进行中的审批。 */
    boolean isRunning(String bizType, Long bizId);
}
