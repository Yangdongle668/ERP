package com.erp.module.shipping.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 出货单状态（11-03 第 2 节） */
public enum ShipmentStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("待审批", DocStatus.PENDING_APPROVAL),
    SUBMITTED("待出库", DocStatus.APPROVED),
    SHIPPED("已出货", DocStatus.IN_PROGRESS),
    COMPLETED("已完成", DocStatus.COMPLETED),
    VOIDED("已作废", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    ShipmentStatus(String label, DocStatus docStatus) {
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
