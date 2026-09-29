package com.erp.module.quality.service;

import com.erp.common.statemachine.StateMachine;

/** 检验标准状态 */
public enum StdStatus implements StateMachine.Labeled {
    DRAFT("草稿"),
    EFFECTIVE("生效"),
    OBSOLETE("作废");

    private final String label;

    StdStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
