package com.erp.module.finance.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 凭证状态（12-06） */
public enum VoucherStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    AUDITED("已审核", DocStatus.APPROVED),
    POSTED("已过账", DocStatus.COMPLETED);

    private final String label;
    private final DocStatus docStatus;

    VoucherStatus(String label, DocStatus docStatus) {
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
