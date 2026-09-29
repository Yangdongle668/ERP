package com.erp.module.pmc.service;

import com.erp.common.statemachine.StateMachine;

/** 交期预警处理状态 */
public enum AlertStatus implements StateMachine.Labeled {
    OPEN("未处理"),
    HANDLED("已处理"),
    IGNORED("已忽略"),
    CLOSED("已消除");

    private final String label;

    AlertStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
