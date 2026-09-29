package com.erp.module.quality.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 检验单状态（10-02 第 4 节） */
public enum InspStatus implements StateMachine.Labeled {
    PENDING("待检", DocStatus.DRAFT),
    INSPECTING("检验中", DocStatus.IN_PROGRESS),
    WAIT_MRB("待 MRB", DocStatus.PENDING_APPROVAL),
    JUDGED("已判定", DocStatus.APPROVED),
    HANDLED("已处理", DocStatus.COMPLETED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    private final DocStatus docStatus;

    InspStatus(String label, DocStatus docStatus) {
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
