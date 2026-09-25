package com.erp.module.purchase.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

import static com.erp.common.enums.DocStatus.*;
import static com.erp.module.purchase.service.PurAction.*;

/** 资材单据状态机（需求 07 各单据的状态图） */
public final class PurStateMachines {

    private PurStateMachines() {
    }

    private static StateMachine.Builder<DocStatus, PurAction> approvable() {
        return StateMachine.builder(DocStatus.class, PurAction.class)
                .transition(DRAFT, SUBMIT, PENDING_APPROVAL)
                .transition(DRAFT, VOID, VOIDED)
                .transition(PENDING_APPROVAL, WITHDRAW, DRAFT)
                .transition(PENDING_APPROVAL, REJECT, DRAFT)
                .transition(PENDING_APPROVAL, APPROVE, APPROVED);
    }

    /** 调价单、订单变更单：草稿 → 待审批 → 已审核（生效）；草稿可作废 */
    public static final StateMachine<DocStatus, PurAction> SIMPLE = approvable().build();

    /** 采购申请、采购订单：已审核 → 执行中（下游开始引用）→ 已完成；下游撤销时恢复执行；已审核/执行中可关闭 */
    public static final StateMachine<DocStatus, PurAction> EXECUTABLE = approvable()
            .transition(APPROVED, UNAPPROVE, DRAFT)
            .transition(APPROVED, START, IN_PROGRESS)
            .transition(APPROVED, COMPLETE, COMPLETED)
            .transition(IN_PROGRESS, COMPLETE, COMPLETED)
            .transition(IN_PROGRESS, REOPEN, APPROVED)
            .transition(COMPLETED, REOPEN, IN_PROGRESS)
            .transition(APPROVED, CLOSE, CLOSED)
            .transition(IN_PROGRESS, CLOSE, CLOSED)
            .build();

    /** 委外单：已审核 → 执行中（发料或收货）→ 已完成（核销） */
    public static final StateMachine<DocStatus, PurAction> OUTSOURCING = approvable()
            .transition(APPROVED, UNAPPROVE, DRAFT)
            .transition(APPROVED, START, IN_PROGRESS)
            .transition(IN_PROGRESS, COMPLETE, COMPLETED)
            .transition(APPROVED, CLOSE, CLOSED)
            .transition(IN_PROGRESS, CLOSE, CLOSED)
            .build();

    /** 到货单：草稿 → 已审核（生成入库单）→ 已完成（入库与检验处理完毕）；入库反确认时恢复为已审核 */
    public static final StateMachine<DocStatus, PurAction> RECEIPT = StateMachine.builder(DocStatus.class, PurAction.class)
            .transition(DRAFT, APPROVE, APPROVED)
            .transition(APPROVED, UNAPPROVE, DRAFT)
            .transition(APPROVED, COMPLETE, COMPLETED)
            .transition(COMPLETED, REOPEN, APPROVED)
            .build();

    /** 采购退货：已审核（生成出库单）→ 已完成（出库确认）；出库未确认时可作废 */
    public static final StateMachine<DocStatus, PurAction> RETURN = approvable()
            .transition(APPROVED, COMPLETE, COMPLETED)
            .transition(COMPLETED, REOPEN, APPROVED)
            .transition(APPROVED, VOID, VOIDED)
            .build();

    /** 对账单：已审核 → 已确认（COMPLETED，供应商确认）；可取消确认、反审核 */
    public static final StateMachine<DocStatus, PurAction> STATEMENT = approvable()
            .transition(APPROVED, UNAPPROVE, DRAFT)
            .transition(APPROVED, CONFIRM, COMPLETED)
            .transition(COMPLETED, UNCONFIRM, APPROVED)
            .build();
}
