package com.erp.module.finance.service;

import com.erp.common.statemachine.StateMachine;

import static com.erp.module.finance.service.FinAction.*;

/** 财务状态机 */
public final class FinStateMachines {

    private FinStateMachines() {
    }

    /** 应收单 / 应付单：事件生成的草稿直接确认；其他应收 / 应付走审批 */
    public static final StateMachine<ArStatus, FinAction> AR = StateMachine.builder(ArStatus.class, FinAction.class)
            .transition(ArStatus.DRAFT, CONFIRM, ArStatus.CONFIRMED)
            .transition(ArStatus.DRAFT, SUBMIT, ArStatus.PENDING)
            .transition(ArStatus.PENDING, WITHDRAW, ArStatus.DRAFT)
            .transition(ArStatus.PENDING, REJECT, ArStatus.DRAFT)
            .transition(ArStatus.PENDING, APPROVE, ArStatus.CONFIRMED)
            .transition(ArStatus.CONFIRMED, UNCONFIRM, ArStatus.DRAFT)
            .transition(ArStatus.DRAFT, VOID, ArStatus.VOIDED)
            // 出货反确认：已确认未核销未开票的应收直接作废
            .transition(ArStatus.CONFIRMED, VOID, ArStatus.VOIDED)
            .build();

    /** 收款单 / 付款单 */
    public static final StateMachine<CashStatus, FinAction> CASH = StateMachine.builder(CashStatus.class, FinAction.class)
            .transition(CashStatus.DRAFT, CONFIRM, CashStatus.CONFIRMED)
            .transition(CashStatus.CONFIRMED, UNCONFIRM, CashStatus.DRAFT)
            .transition(CashStatus.DRAFT, VOID, CashStatus.VOIDED)
            .build();

    /** 付款申请 */
    public static final StateMachine<RequestStatus, FinAction> REQUEST = StateMachine.builder(RequestStatus.class, FinAction.class)
            .transition(RequestStatus.DRAFT, SUBMIT, RequestStatus.PENDING)
            .transition(RequestStatus.PENDING, WITHDRAW, RequestStatus.DRAFT)
            .transition(RequestStatus.PENDING, REJECT, RequestStatus.DRAFT)
            .transition(RequestStatus.PENDING, APPROVE, RequestStatus.APPROVED)
            .transition(RequestStatus.APPROVED, PAY, RequestStatus.PARTIAL)
            .transition(RequestStatus.APPROVED, PAY_ALL, RequestStatus.PAID)
            .transition(RequestStatus.PARTIAL, PAY, RequestStatus.PARTIAL)
            .transition(RequestStatus.PARTIAL, PAY_ALL, RequestStatus.PAID)
            .transition(RequestStatus.PAID, UNPAY, RequestStatus.PARTIAL)
            .transition(RequestStatus.PARTIAL, UNPAY, RequestStatus.PARTIAL)
            .transition(RequestStatus.PARTIAL, UNPAY_ALL, RequestStatus.APPROVED)
            .transition(RequestStatus.PAID, UNPAY_ALL, RequestStatus.APPROVED)
            .transition(RequestStatus.APPROVED, CLOSE, RequestStatus.CLOSED)
            .transition(RequestStatus.PARTIAL, CLOSE, RequestStatus.CLOSED)
            .transition(RequestStatus.DRAFT, VOID, RequestStatus.VOIDED)
            .build();
}
