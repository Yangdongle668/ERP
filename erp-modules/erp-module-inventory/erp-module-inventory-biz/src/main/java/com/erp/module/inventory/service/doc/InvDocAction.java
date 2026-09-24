package com.erp.module.inventory.service.doc;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/**
 * 仓库单据动作与状态机（需求 08-03/04/05 状态说明）：
 * 草稿 →（手工其他出入库：待审批 → 已审核）→ 已完成（已入库/已出库/已调拨）；
 * 草稿可作废或退回（业务生成）；已完成可反确认（业务生成回到草稿，手工回到已审核）。
 */
public enum InvDocAction implements StateMachine.Labeled {
    SUBMIT("提交"), APPROVE("审核"), REJECT("驳回"), WITHDRAW("撤回"), CONFIRM("确认"), RETURN("退回"),
    UNCONFIRM("反确认"), UNCONFIRM_MANUAL("反确认"), VOID("作废");

    private final String label;

    InvDocAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    public static final StateMachine<DocStatus, InvDocAction> MACHINE = StateMachine.builder(DocStatus.class, InvDocAction.class)
            .transition(DocStatus.DRAFT, SUBMIT, DocStatus.PENDING_APPROVAL)
            .transition(DocStatus.DRAFT, APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.PENDING_APPROVAL, APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.PENDING_APPROVAL, REJECT, DocStatus.DRAFT)
            .transition(DocStatus.PENDING_APPROVAL, WITHDRAW, DocStatus.DRAFT)
            .transition(DocStatus.DRAFT, CONFIRM, DocStatus.COMPLETED)
            .transition(DocStatus.APPROVED, CONFIRM, DocStatus.COMPLETED)
            .transition(DocStatus.DRAFT, RETURN, DocStatus.VOIDED)
            .transition(DocStatus.DRAFT, VOID, DocStatus.VOIDED)
            .transition(DocStatus.APPROVED, VOID, DocStatus.VOIDED)
            .transition(DocStatus.COMPLETED, UNCONFIRM, DocStatus.DRAFT)
            .transition(DocStatus.COMPLETED, UNCONFIRM_MANUAL, DocStatus.APPROVED)
            .build();
}
