package com.erp.module.sales.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 报价单状态（需求 04-02）：草稿 / 审批中 / 已审核 / 已发送 / 已成交 / 未成交 / 已过期 / 已修订 */
public enum QuoteStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("审批中", DocStatus.PENDING_APPROVAL),
    APPROVED("已审核", DocStatus.APPROVED),
    SENT("已发送", DocStatus.APPROVED),
    WON("已成交", DocStatus.COMPLETED),
    LOST("未成交", DocStatus.CLOSED),
    EXPIRED("已过期", DocStatus.CLOSED),
    REVISED("已修订", DocStatus.CLOSED);

    private final String label;
    private final DocStatus docStatus;

    QuoteStatus(String label, DocStatus docStatus) {
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

    /** 可以转订单、发送、未成交的状态 */
    public boolean isEffective() {
        return this == APPROVED || this == SENT;
    }
}
