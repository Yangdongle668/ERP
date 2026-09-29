package com.erp.module.pmc.service;

import com.erp.common.statemachine.StateMachine;

/** PMC 状态机 */
public final class PmcStateMachines {

    private PmcStateMachines() {
    }

    /** MPS / 出货计划：草稿 → 已发布 → 已关闭（已发布的调整复制为新草稿） */
    public static final StateMachine<PlanStatus, PmcAction> PLAN = StateMachine.builder(PlanStatus.class, PmcAction.class)
            .transition(PlanStatus.DRAFT, PmcAction.PUBLISH, PlanStatus.PUBLISHED)
            .transition(PlanStatus.PUBLISHED, PmcAction.CLOSE, PlanStatus.CLOSED)
            .build();

    /** 交期预警：处理 / 忽略；延期消除自动关闭；已处理的延期继续增加超过 3 天重新打开 */
    public static final StateMachine<AlertStatus, PmcAction> ALERT = StateMachine.builder(AlertStatus.class, PmcAction.class)
            .transition(AlertStatus.OPEN, PmcAction.HANDLE, AlertStatus.HANDLED)
            .transition(AlertStatus.OPEN, PmcAction.IGNORE, AlertStatus.IGNORED)
            .transition(AlertStatus.HANDLED, PmcAction.HANDLE, AlertStatus.HANDLED)
            .transition(AlertStatus.OPEN, PmcAction.RESOLVE, AlertStatus.CLOSED)
            .transition(AlertStatus.HANDLED, PmcAction.RESOLVE, AlertStatus.CLOSED)
            .transition(AlertStatus.IGNORED, PmcAction.RESOLVE, AlertStatus.CLOSED)
            .transition(AlertStatus.HANDLED, PmcAction.REOPEN, AlertStatus.OPEN)
            .transition(AlertStatus.IGNORED, PmcAction.REOPEN, AlertStatus.OPEN)
            .transition(AlertStatus.CLOSED, PmcAction.REOPEN, AlertStatus.OPEN)
            .build();
}
