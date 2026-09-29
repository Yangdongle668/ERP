package com.erp.module.production.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

import static com.erp.module.production.service.MfgAction.APPROVE;
import static com.erp.module.production.service.MfgAction.CANCEL;
import static com.erp.module.production.service.MfgAction.CLOSE;
import static com.erp.module.production.service.MfgAction.COMPLETE;
import static com.erp.module.production.service.MfgAction.REJECT;
import static com.erp.module.production.service.MfgAction.REOPEN;
import static com.erp.module.production.service.MfgAction.SUBMIT;
import static com.erp.module.production.service.MfgAction.UNAPPROVE;
import static com.erp.module.production.service.MfgAction.VOID;
import static com.erp.module.production.service.MfgAction.WITHDRAW;

/** 生产单据状态机（需求 09 各单据的状态流转） */
public final class MfgStateMachines {

    private MfgStateMachines() {
    }

    /** 生产订单：见需求 09-01 第 4 节；暂停恢复到暂停前的状态（RESUME → 已下达，RESUME_RUN → 生产中） */
    public static final StateMachine<ProdStatus, MfgAction> PROD_ORDER = StateMachine.builder(ProdStatus.class, MfgAction.class)
            .transition(ProdStatus.DRAFT, SUBMIT, ProdStatus.PENDING)
            .transition(ProdStatus.DRAFT, VOID, ProdStatus.VOIDED)
            .transition(ProdStatus.PENDING, WITHDRAW, ProdStatus.DRAFT)
            .transition(ProdStatus.PENDING, REJECT, ProdStatus.DRAFT)
            .transition(ProdStatus.PENDING, APPROVE, ProdStatus.PLANNED)
            .transition(ProdStatus.PLANNED, VOID, ProdStatus.VOIDED)
            .transition(ProdStatus.PLANNED, MfgAction.RELEASE, ProdStatus.RELEASED)
            .transition(ProdStatus.RELEASED, MfgAction.UNRELEASE, ProdStatus.PLANNED)
            .transition(ProdStatus.RELEASED, MfgAction.START, ProdStatus.IN_PROGRESS)
            .transition(ProdStatus.RELEASED, MfgAction.SUSPEND, ProdStatus.SUSPENDED)
            .transition(ProdStatus.IN_PROGRESS, MfgAction.SUSPEND, ProdStatus.SUSPENDED)
            .transition(ProdStatus.SUSPENDED, MfgAction.RESUME, ProdStatus.RELEASED)
            .transition(ProdStatus.SUSPENDED, MfgAction.RESUME_RUN, ProdStatus.IN_PROGRESS)
            .transition(ProdStatus.RELEASED, COMPLETE, ProdStatus.COMPLETED)
            .transition(ProdStatus.IN_PROGRESS, COMPLETE, ProdStatus.COMPLETED)
            .transition(ProdStatus.COMPLETED, REOPEN, ProdStatus.IN_PROGRESS)
            .transition(ProdStatus.RELEASED, CLOSE, ProdStatus.CLOSED)
            .transition(ProdStatus.IN_PROGRESS, CLOSE, ProdStatus.CLOSED)
            .transition(ProdStatus.SUSPENDED, CLOSE, ProdStatus.CLOSED)
            .transition(ProdStatus.COMPLETED, CLOSE, ProdStatus.CLOSED)
            .build();

    /**
     * 领料单 / 退料单：草稿 → 待审批（超领）→ 已提交（APPROVED，已生成仓库单据）→ 已完成（出/入库确认）；
     * 已提交且仓库单据未确认可撤回；仓库退回单据时作废。
     */
    public static final StateMachine<DocStatus, MfgAction> MATERIAL_DOC = StateMachine.builder(DocStatus.class, MfgAction.class)
            .transition(DocStatus.DRAFT, SUBMIT, DocStatus.PENDING_APPROVAL)
            .transition(DocStatus.DRAFT, VOID, DocStatus.VOIDED)
            .transition(DocStatus.PENDING_APPROVAL, WITHDRAW, DocStatus.DRAFT)
            .transition(DocStatus.PENDING_APPROVAL, REJECT, DocStatus.DRAFT)
            .transition(DocStatus.PENDING_APPROVAL, APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.APPROVED, WITHDRAW, DocStatus.DRAFT)
            .transition(DocStatus.APPROVED, COMPLETE, DocStatus.COMPLETED)
            .transition(DocStatus.COMPLETED, REOPEN, DocStatus.APPROVED)
            .transition(DocStatus.APPROVED, VOID, DocStatus.VOIDED)
            .build();

    /** 报工单：草稿 → 已审核；已审核可反审核 */
    public static final StateMachine<DocStatus, MfgAction> REPORT = StateMachine.builder(DocStatus.class, MfgAction.class)
            .transition(DocStatus.DRAFT, APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.APPROVED, UNAPPROVE, DocStatus.DRAFT)
            .build();

    /** 工单 */
    public static final StateMachine<WoStatus, MfgAction> WORK_ORDER = StateMachine.builder(WoStatus.class, MfgAction.class)
            .transition(WoStatus.DISPATCHED, MfgAction.START, WoStatus.RUNNING)
            .transition(WoStatus.DISPATCHED, COMPLETE, WoStatus.DONE)
            .transition(WoStatus.RUNNING, COMPLETE, WoStatus.DONE)
            .transition(WoStatus.DONE, REOPEN, WoStatus.RUNNING)
            .transition(WoStatus.RUNNING, MfgAction.RESET, WoStatus.DISPATCHED)
            .transition(WoStatus.DISPATCHED, CANCEL, WoStatus.CANCELED)
            .build();

    /** 完工入库申请 */
    public static final StateMachine<FinishStatus, MfgAction> FINISH = StateMachine.builder(FinishStatus.class, MfgAction.class)
            .transition(FinishStatus.SUBMITTED, MfgAction.STOCK, FinishStatus.STOCKED)
            .transition(FinishStatus.SUBMITTED, MfgAction.JUDGE, FinishStatus.JUDGED)
            .transition(FinishStatus.STOCKED, MfgAction.JUDGE, FinishStatus.JUDGED)
            .transition(FinishStatus.JUDGED, MfgAction.UNJUDGE, FinishStatus.STOCKED)
            .transition(FinishStatus.STOCKED, MfgAction.UNSTOCK, FinishStatus.SUBMITTED)
            .transition(FinishStatus.SUBMITTED, CANCEL, FinishStatus.CANCELED)
            .build();
}
