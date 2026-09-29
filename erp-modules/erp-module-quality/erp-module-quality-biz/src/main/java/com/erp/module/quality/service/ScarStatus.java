package com.erp.module.quality.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** SCAR 状态 */
public enum ScarStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    SENT("已发出", DocStatus.IN_PROGRESS),
    REPLIED("已回复", DocStatus.IN_PROGRESS),
    VERIFYING("验证中", DocStatus.IN_PROGRESS),
    CLOSED("已结案", DocStatus.CLOSED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    ScarStatus(String label, DocStatus docStatus) {
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
