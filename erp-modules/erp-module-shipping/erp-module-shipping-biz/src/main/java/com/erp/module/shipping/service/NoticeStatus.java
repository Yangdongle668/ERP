package com.erp.module.shipping.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 出货通知状态（11-01 第 2 节） */
public enum NoticeStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("待审批", DocStatus.PENDING_APPROVAL),
    APPROVED("已审核", DocStatus.APPROVED),
    PICKING("拣货中", DocStatus.IN_PROGRESS),
    PACKED("已装箱", DocStatus.IN_PROGRESS),
    OQC("待 OQC", DocStatus.IN_PROGRESS),
    READY("待出货", DocStatus.IN_PROGRESS),
    SHIPPED("已出货", DocStatus.COMPLETED),
    CLOSED("已关闭", DocStatus.CLOSED),
    VOIDED("已作废", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    NoticeStatus(String label, DocStatus docStatus) {
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
