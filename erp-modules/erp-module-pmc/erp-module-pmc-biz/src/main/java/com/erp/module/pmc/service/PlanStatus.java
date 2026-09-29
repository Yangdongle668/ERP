package com.erp.module.pmc.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

/** MPS、出货计划的状态 */
public enum PlanStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PUBLISHED("已发布", DocStatus.APPROVED),
    CLOSED("已关闭", DocStatus.CLOSED);

    private final String label;
    private final DocStatus docStatus;

    PlanStatus(String label, DocStatus docStatus) {
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
