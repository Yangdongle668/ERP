package com.erp.common.statemachine;

import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;

import static com.erp.common.enums.DocAction.*;
import static com.erp.common.enums.DocStatus.*;

/** 预置的单据状态机。 */
public final class DocStateMachines {

    /**
     * 标准单据状态机（需求文档 00 第 4.2 节）。
     * <p>「未配置审批流时提交即审核」由业务服务先 SUBMIT 再 APPROVE 实现，状态机本身不做特殊处理。
     */
    public static final StateMachine<DocStatus, DocAction> STANDARD =
            StateMachine.builder(DocStatus.class, DocAction.class)
                    .transition(DRAFT, SUBMIT, PENDING_APPROVAL)
                    .transition(DRAFT, VOID, VOIDED)
                    .transition(PENDING_APPROVAL, WITHDRAW, DRAFT)
                    .transition(PENDING_APPROVAL, REJECT, DRAFT)
                    .transition(PENDING_APPROVAL, APPROVE, APPROVED)
                    .transition(APPROVED, UNAPPROVE, DRAFT)
                    .transition(APPROVED, START, IN_PROGRESS)
                    .transition(APPROVED, COMPLETE, COMPLETED)
                    .transition(APPROVED, CLOSE, CLOSED)
                    .transition(IN_PROGRESS, COMPLETE, COMPLETED)
                    .transition(IN_PROGRESS, CLOSE, CLOSED)
                    .build();

    private DocStateMachines() {
    }
}
