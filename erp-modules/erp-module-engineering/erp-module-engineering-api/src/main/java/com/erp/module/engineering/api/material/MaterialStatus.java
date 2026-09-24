package com.erp.module.engineering.api.material;

import com.erp.common.statemachine.StateMachine;

public enum MaterialStatus implements StateMachine.Labeled {
    DRAFT("草稿"),
    /** 参数 eng.material.enable-approval 为是时，草稿提交启用后进入审批 */
    PENDING("待审批"),
    ENABLED("启用"),
    DISABLED("停用");

    private final String label;

    MaterialStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
