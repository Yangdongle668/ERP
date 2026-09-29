package com.erp.module.finance.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 应收单 / 应付单状态（12-02、12-04） */
public enum ArStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("待审批", DocStatus.PENDING_APPROVAL),
    CONFIRMED("已确认", DocStatus.APPROVED),
    VOIDED("已作废", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    ArStatus(String label, DocStatus docStatus) {
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
