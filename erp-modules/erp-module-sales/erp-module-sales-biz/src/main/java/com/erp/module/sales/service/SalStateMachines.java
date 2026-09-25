package com.erp.module.sales.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

import static com.erp.common.enums.DocStatus.APPROVED;
import static com.erp.common.enums.DocStatus.CLOSED;
import static com.erp.common.enums.DocStatus.COMPLETED;
import static com.erp.common.enums.DocStatus.DRAFT;
import static com.erp.common.enums.DocStatus.IN_PROGRESS;
import static com.erp.common.enums.DocStatus.PENDING_APPROVAL;
import static com.erp.common.enums.DocStatus.VOIDED;
import static com.erp.module.sales.service.SalAction.APPROVE;
import static com.erp.module.sales.service.SalAction.CLOSE;
import static com.erp.module.sales.service.SalAction.COMPLETE;
import static com.erp.module.sales.service.SalAction.PUBLISH;
import static com.erp.module.sales.service.SalAction.REJECT;
import static com.erp.module.sales.service.SalAction.REOPEN;
import static com.erp.module.sales.service.SalAction.START;
import static com.erp.module.sales.service.SalAction.SUBMIT;
import static com.erp.module.sales.service.SalAction.UNAPPROVE;
import static com.erp.module.sales.service.SalAction.VOID;
import static com.erp.module.sales.service.SalAction.WITHDRAW;

/** 销售单据状态机（需求 04 各单据的状态流转） */
public final class SalStateMachines {

    private SalStateMachines() {
    }

    private static StateMachine.Builder<DocStatus, SalAction> approvable() {
        return StateMachine.builder(DocStatus.class, SalAction.class)
                .transition(DRAFT, SUBMIT, PENDING_APPROVAL)
                .transition(DRAFT, VOID, VOIDED)
                .transition(PENDING_APPROVAL, WITHDRAW, DRAFT)
                .transition(PENDING_APPROVAL, REJECT, DRAFT)
                .transition(PENDING_APPROVAL, APPROVE, APPROVED);
    }

    /** 订单变更单：草稿 → 待审批 → 已审核（已应用）；草稿可作废 */
    public static final StateMachine<DocStatus, SalAction> SIMPLE = approvable().build();

    /** 价格表：已审核（生效）→ 已关闭（手工失效） */
    public static final StateMachine<DocStatus, SalAction> PRICE_LIST = approvable()
            .transition(APPROVED, CLOSE, CLOSED)
            .build();

    /** 销售订单：已审核 → 执行中（首次出货通知/出货）→ 已完成；已审核/执行中可关闭；已审核可反审核 */
    public static final StateMachine<DocStatus, SalAction> ORDER = approvable()
            .transition(APPROVED, UNAPPROVE, DRAFT)
            .transition(APPROVED, START, IN_PROGRESS)
            .transition(APPROVED, COMPLETE, COMPLETED)
            .transition(IN_PROGRESS, COMPLETE, COMPLETED)
            .transition(IN_PROGRESS, REOPEN, APPROVED)
            .transition(COMPLETED, REOPEN, IN_PROGRESS)
            .transition(APPROVED, CLOSE, CLOSED)
            .transition(IN_PROGRESS, CLOSE, CLOSED)
            .build();

    /** 销售退货：已审核（生成入库单）→ 已完成（入库并判定完成）；入库未确认时可作废 */
    public static final StateMachine<DocStatus, SalAction> RETURN = approvable()
            .transition(APPROVED, COMPLETE, COMPLETED)
            .transition(COMPLETED, REOPEN, APPROVED)
            .transition(APPROVED, VOID, VOIDED)
            .build();

    /** 销售预测：草稿 → 已发布（APPROVED）→ 已关闭 */
    public static final StateMachine<DocStatus, SalAction> FORECAST = StateMachine.builder(DocStatus.class, SalAction.class)
            .transition(DRAFT, PUBLISH, APPROVED)
            .transition(APPROVED, CLOSE, CLOSED)
            .build();

    /** RFQ：草稿 → 评估中（分派）→ 已核算 → 已报价；未关闭的可关闭 */
    public static final StateMachine<RfqStatus, SalAction> RFQ = StateMachine.builder(RfqStatus.class, SalAction.class)
            .transition(RfqStatus.DRAFT, SalAction.ASSIGN, RfqStatus.EVALUATING)
            .transition(RfqStatus.EVALUATING, SalAction.ASSIGN, RfqStatus.EVALUATING)
            .transition(RfqStatus.EVALUATING, SalAction.COST, RfqStatus.COSTED)
            .transition(RfqStatus.DRAFT, SalAction.QUOTE, RfqStatus.QUOTED)
            .transition(RfqStatus.EVALUATING, SalAction.QUOTE, RfqStatus.QUOTED)
            .transition(RfqStatus.COSTED, SalAction.QUOTE, RfqStatus.QUOTED)
            .transition(RfqStatus.QUOTED, SalAction.QUOTE, RfqStatus.QUOTED)
            .transition(RfqStatus.DRAFT, CLOSE, RfqStatus.CLOSED)
            .transition(RfqStatus.EVALUATING, CLOSE, RfqStatus.CLOSED)
            .transition(RfqStatus.COSTED, CLOSE, RfqStatus.CLOSED)
            .transition(RfqStatus.QUOTED, CLOSE, RfqStatus.CLOSED)
            .build();

    /** 报价单：见 {@link QuoteStatus} */
    public static final StateMachine<QuoteStatus, SalAction> QUOTATION = StateMachine.builder(QuoteStatus.class, SalAction.class)
            .transition(QuoteStatus.DRAFT, SUBMIT, QuoteStatus.PENDING)
            .transition(QuoteStatus.PENDING, WITHDRAW, QuoteStatus.DRAFT)
            .transition(QuoteStatus.PENDING, REJECT, QuoteStatus.DRAFT)
            .transition(QuoteStatus.PENDING, APPROVE, QuoteStatus.APPROVED)
            .transition(QuoteStatus.APPROVED, SalAction.SEND, QuoteStatus.SENT)
            .transition(QuoteStatus.SENT, SalAction.SEND, QuoteStatus.SENT)
            .transition(QuoteStatus.APPROVED, SalAction.WIN, QuoteStatus.WON)
            .transition(QuoteStatus.SENT, SalAction.WIN, QuoteStatus.WON)
            .transition(QuoteStatus.APPROVED, SalAction.LOSE, QuoteStatus.LOST)
            .transition(QuoteStatus.SENT, SalAction.LOSE, QuoteStatus.LOST)
            .transition(QuoteStatus.APPROVED, SalAction.EXPIRE, QuoteStatus.EXPIRED)
            .transition(QuoteStatus.SENT, SalAction.EXPIRE, QuoteStatus.EXPIRED)
            .transition(QuoteStatus.APPROVED, SalAction.REVISE, QuoteStatus.REVISED)
            .transition(QuoteStatus.SENT, SalAction.REVISE, QuoteStatus.REVISED)
            .transition(QuoteStatus.EXPIRED, SalAction.REVISE, QuoteStatus.REVISED)
            .build();
}
