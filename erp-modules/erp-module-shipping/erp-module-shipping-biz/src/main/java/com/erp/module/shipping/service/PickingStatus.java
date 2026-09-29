package com.erp.module.shipping.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 拣货单状态（11-02 第 2 节） */
public enum PickingStatus implements StateMachine.Labeled {
    WAITING("待拣", DocStatus.APPROVED),
    PICKING("拣货中", DocStatus.IN_PROGRESS),
    DONE("已完成", DocStatus.COMPLETED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    PickingStatus(String label, DocStatus docStatus) {
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
