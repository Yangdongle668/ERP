package com.erp.module.sales.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** RFQ 状态（需求 04-02）：草稿 / 评估中 / 已核算 / 已报价 / 已关闭 */
public enum RfqStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    EVALUATING("评估中", DocStatus.IN_PROGRESS),
    COSTED("已核算", DocStatus.IN_PROGRESS),
    QUOTED("已报价", DocStatus.COMPLETED),
    CLOSED("已关闭", DocStatus.CLOSED);

    private final String label;
    private final DocStatus docStatus;

    RfqStatus(String label, DocStatus docStatus) {
        this.label = label;
        this.docStatus = docStatus;
    }

    @Override
    public String label() {
        return label;
    }

    /** 通用状态（列表筛选、操作日志） */
    public DocStatus docStatus() {
        return docStatus;
    }
}
