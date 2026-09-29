package com.erp.module.production.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 完工入库申请状态（需求 09-05）：已提交 / 已入库 / 已判定 / 已取消 */
public enum FinishStatus implements StateMachine.Labeled {
    SUBMITTED("已提交", DocStatus.APPROVED),
    STOCKED("已入库", DocStatus.IN_PROGRESS),
    JUDGED("已判定", DocStatus.COMPLETED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    FinishStatus(String label, DocStatus docStatus) {
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
