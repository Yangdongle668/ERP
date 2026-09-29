package com.erp.module.production.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 工单状态（需求 09-02）：已派工 / 进行中 / 已完成 / 已取消 */
public enum WoStatus implements StateMachine.Labeled {
    DISPATCHED("已派工", DocStatus.APPROVED),
    RUNNING("进行中", DocStatus.IN_PROGRESS),
    DONE("已完成", DocStatus.COMPLETED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    WoStatus(String label, DocStatus docStatus) {
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
