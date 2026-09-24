package com.erp.module.engineering.api.material;

import com.erp.common.statemachine.StateMachine;

public enum MaterialStatus implements StateMachine.Labeled {
    DRAFT("草稿"),
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
