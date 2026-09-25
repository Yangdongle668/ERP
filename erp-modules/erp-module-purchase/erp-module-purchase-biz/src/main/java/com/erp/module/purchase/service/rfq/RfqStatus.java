package com.erp.module.purchase.service.rfq;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** 询价单状态（需求 07-04）：草稿 / 报价中 / 比价中 / 已定标 / 已取消 */
public enum RfqStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    QUOTING("报价中", DocStatus.IN_PROGRESS),
    COMPARING("比价中", DocStatus.IN_PROGRESS),
    AWARDED("已定标", DocStatus.COMPLETED),
    CANCELED("已取消", DocStatus.VOIDED);

    private final String label;
    /** 对应的通用单据状态（单头 status 字段，用于统计口径） */
    private final DocStatus docStatus;

    RfqStatus(String label, DocStatus docStatus) {
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
