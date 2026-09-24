package com.erp.module.system.api.workflow;

import java.util.Map;
import java.util.Optional;

/**
 * 审批流（需求 01-系统管理/08-审批流）。
 *
 * <p>业务模块在“提交”时于同一事务中调用 {@link #start}；{@link StartResult#isStarted()} 为 false 时直接审核，
 * 为 true 时单据置为“待审批”，最终结果以 {@link ApprovalCompletedEvent} 通知（在审批动作的同一事务内同步发布，
 * 业务处理失败时审批动作整体回滚）。待审批的单据不能删除或作废，必须先撤回（R13，由业务模块提示）。
 */
public interface WorkflowApi {

    /**
     * @param variables   条件字段值（与 ApprovalBizDefinition 声明的字段编码一致）
     * @param bizUsers    单据中的用户字段（用于“单据指定字段”审批人），如 salespersonId → 用户 ID
     * @param initiatorId 发起人（通常为当前用户）
     */
    StartResult start(String bizType, Long bizId, String bizNo, String title,
                      Map<String, Object> variables, Map<String, Long> bizUsers, Long initiatorId);

    /** 发起人撤回（业务单据的“撤回”按钮调用）；只有发起人可以撤回（R07）。 */
    void withdraw(String bizType, Long bizId, Long operatorId);

    /** 单据当前是否有进行中的审批。 */
    boolean isRunning(String bizType, Long bizId);

    /** 单据当前审批状态（没有进行中的审批时为空）。 */
    Optional<ApprovalSummary> getRunning(String bizType, Long bizId);
}
