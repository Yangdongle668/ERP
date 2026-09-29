package com.erp.module.quality.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** CAPA 状态 */
public enum CapaStatus implements StateMachine.Labeled {
    OPEN("进行中", DocStatus.IN_PROGRESS),
    VERIFYING("待验证", DocStatus.IN_PROGRESS),
    CLOSED("已结案", DocStatus.CLOSED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    CapaStatus(String label, DocStatus docStatus) {
        this.label = label;
        this.docStatus = docStatus;
    }

    @Override
    public String label() {
        return label;
    }

    /** 单据通用状态（BaseDocDO.status）随业务状态同步 */
    public DocStatus docStatus() {
        return docStatus;
    }
}
