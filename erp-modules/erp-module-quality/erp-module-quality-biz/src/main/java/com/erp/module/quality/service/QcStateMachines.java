package com.erp.module.quality.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

import static com.erp.module.quality.service.QcAction.*;

/** 品质状态机 */
public final class QcStateMachines {

    private QcStateMachines() {
    }

    /** 检验单（10-02 第 4 节） */
    public static final StateMachine<InspStatus, QcAction> INSPECTION = StateMachine.builder(InspStatus.class, QcAction.class)
            .transition(InspStatus.PENDING, START, InspStatus.INSPECTING)
            .transition(InspStatus.PENDING, JUDGE, InspStatus.JUDGED)
            .transition(InspStatus.INSPECTING, JUDGE, InspStatus.JUDGED)
            .transition(InspStatus.PENDING, TO_MRB, InspStatus.WAIT_MRB)
            .transition(InspStatus.INSPECTING, TO_MRB, InspStatus.WAIT_MRB)
            .transition(InspStatus.WAIT_MRB, MRB_JUDGE, InspStatus.JUDGED)
            .transition(InspStatus.WAIT_MRB, MRB_SORT, InspStatus.INSPECTING)
            .transition(InspStatus.WAIT_MRB, MRB_BACK, InspStatus.INSPECTING)
            .transition(InspStatus.JUDGED, HANDLE, InspStatus.HANDLED)
            .transition(InspStatus.JUDGED, REJUDGE, InspStatus.INSPECTING)
            .transition(InspStatus.PENDING, CANCEL, InspStatus.CANCELED)
            .transition(InspStatus.INSPECTING, CANCEL, InspStatus.CANCELED)
            .build();

    /** 检验标准：草稿 → 生效 → 作废（新版本生效时旧版自动作废） */
    public static final StateMachine<StdStatus, QcAction> STANDARD = StateMachine.builder(StdStatus.class, QcAction.class)
            .transition(StdStatus.DRAFT, ACTIVATE, StdStatus.EFFECTIVE)
            .transition(StdStatus.EFFECTIVE, OBSOLETE, StdStatus.OBSOLETE)
            .build();

    /** NCR：草稿 → 待审批（MRB 会签）→ 已审核（处置执行中）→ 已关闭；草稿可作废 */
    public static final StateMachine<DocStatus, QcAction> NCR = StateMachine.builder(DocStatus.class, QcAction.class)
            .transition(DocStatus.DRAFT, SUBMIT, DocStatus.PENDING_APPROVAL)
            .transition(DocStatus.DRAFT, VOID, DocStatus.VOIDED)
            .transition(DocStatus.PENDING_APPROVAL, WITHDRAW, DocStatus.DRAFT)
            .transition(DocStatus.PENDING_APPROVAL, REJECT, DocStatus.DRAFT)
            .transition(DocStatus.PENDING_APPROVAL, APPROVE, DocStatus.APPROVED)
            .transition(DocStatus.APPROVED, CLOSE, DocStatus.CLOSED)
            .build();

    /** CAPA：D5 完成 → 待验证；验证有效继续 D7、D8，无效退回 D4；D8 完成结案 */
    public static final StateMachine<CapaStatus, QcAction> CAPA = StateMachine.builder(CapaStatus.class, QcAction.class)
            .transition(CapaStatus.OPEN, COMPLETE_STEP, CapaStatus.OPEN)
            .transition(CapaStatus.OPEN, TO_VERIFY, CapaStatus.VERIFYING)
            .transition(CapaStatus.VERIFYING, VERIFY_OK, CapaStatus.OPEN)
            .transition(CapaStatus.VERIFYING, VERIFY_FAIL, CapaStatus.OPEN)
            .transition(CapaStatus.OPEN, CLOSE, CapaStatus.CLOSED)
            .transition(CapaStatus.OPEN, CANCEL, CapaStatus.CANCELED)
            .transition(CapaStatus.VERIFYING, CANCEL, CapaStatus.CANCELED)
            .build();

    /** 客诉：新建 → 分析中 → 已回复 →（结案审批）→ 已结案；未结案可取消 */
    public static final StateMachine<ComplaintStatus, QcAction> COMPLAINT = StateMachine.builder(ComplaintStatus.class, QcAction.class)
            .transition(ComplaintStatus.OPEN, START, ComplaintStatus.ANALYZING)
            .transition(ComplaintStatus.OPEN, REPLY, ComplaintStatus.REPLIED)
            .transition(ComplaintStatus.ANALYZING, REPLY, ComplaintStatus.REPLIED)
            .transition(ComplaintStatus.REPLIED, REPLY, ComplaintStatus.REPLIED)
            .transition(ComplaintStatus.REPLIED, SUBMIT, ComplaintStatus.CLOSING)
            .transition(ComplaintStatus.REPLIED, CLOSE, ComplaintStatus.CLOSED)
            .transition(ComplaintStatus.CLOSING, APPROVE, ComplaintStatus.CLOSED)
            .transition(ComplaintStatus.CLOSING, REJECT, ComplaintStatus.REPLIED)
            .transition(ComplaintStatus.CLOSING, WITHDRAW, ComplaintStatus.REPLIED)
            .transition(ComplaintStatus.OPEN, CANCEL, ComplaintStatus.CANCELED)
            .transition(ComplaintStatus.ANALYZING, CANCEL, ComplaintStatus.CANCELED)
            .transition(ComplaintStatus.REPLIED, CANCEL, ComplaintStatus.CANCELED)
            .build();

    /** SCAR：草稿 → 已发出 → 已回复 → 验证中 → 已结案（无效退回已发出） */
    public static final StateMachine<ScarStatus, QcAction> SCAR = StateMachine.builder(ScarStatus.class, QcAction.class)
            .transition(ScarStatus.DRAFT, SEND, ScarStatus.SENT)
            .transition(ScarStatus.SENT, REPLY, ScarStatus.REPLIED)
            .transition(ScarStatus.REPLIED, START_VERIFY, ScarStatus.VERIFYING)
            .transition(ScarStatus.VERIFYING, VERIFY_OK, ScarStatus.CLOSED)
            .transition(ScarStatus.VERIFYING, VERIFY_FAIL, ScarStatus.SENT)
            .transition(ScarStatus.DRAFT, CANCEL, ScarStatus.CANCELED)
            .transition(ScarStatus.SENT, CANCEL, ScarStatus.CANCELED)
            .transition(ScarStatus.REPLIED, CANCEL, ScarStatus.CANCELED)
            .transition(ScarStatus.VERIFYING, CANCEL, ScarStatus.CANCELED)
            .build();
}
