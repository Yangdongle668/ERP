package com.erp.module.quality.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 客诉状态（CLOSING 为结案审批中） */
public enum ComplaintStatus implements StateMachine.Labeled {
    OPEN("新建", DocStatus.IN_PROGRESS),
    ANALYZING("分析中", DocStatus.IN_PROGRESS),
    REPLIED("已回复", DocStatus.IN_PROGRESS),
    CLOSING("结案审批中", DocStatus.PENDING_APPROVAL),
    CLOSED("已结案", DocStatus.CLOSED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    ComplaintStatus(String label, DocStatus docStatus) {
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
