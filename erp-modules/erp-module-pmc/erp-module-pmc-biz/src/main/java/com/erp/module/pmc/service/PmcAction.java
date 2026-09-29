package com.erp.module.pmc.service;

import com.erp.common.statemachine.StateMachine;

/** PMC 动作 */
public enum PmcAction implements StateMachine.Labeled {
    PUBLISH("发布"),
    CLOSE("关闭"),
    HANDLE("处理"),
    IGNORE("忽略"),
    REOPEN("重新打开"),
    RESOLVE("自动关闭");

    private final String label;

    PmcAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
