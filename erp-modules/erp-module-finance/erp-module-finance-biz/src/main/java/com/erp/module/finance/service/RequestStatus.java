package com.erp.module.finance.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 付款申请状态（12-05） */
public enum RequestStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("待审批", DocStatus.PENDING_APPROVAL),
    APPROVED("待付款", DocStatus.APPROVED),
    PARTIAL("部分付款", DocStatus.IN_PROGRESS),
    PAID("已付款", DocStatus.COMPLETED),
    CLOSED("已关闭", DocStatus.CLOSED),
    VOIDED("已作废", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    RequestStatus(String label, DocStatus docStatus) {
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
