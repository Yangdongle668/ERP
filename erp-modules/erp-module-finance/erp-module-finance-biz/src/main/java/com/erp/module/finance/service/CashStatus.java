package com.erp.module.finance.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 收款单 / 付款单状态（12-03、12-05） */
public enum CashStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    CONFIRMED("已确认", DocStatus.APPROVED),
    VOIDED("已作废", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    CashStatus(String label, DocStatus docStatus) {
        this.label = label;
        this.docStatus = docStatus;
    }

    @Override
    public String label() {
        return label;
    }

    public DocStatus docStatus() {
        return docStatus;
    }
}
