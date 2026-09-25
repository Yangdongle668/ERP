package com.erp.module.purchase.service.score;

import com.erp.common.statemachine.StateMachine;

/** 供应商评估状态（需求 07-10）：已计算（待手工评分）/ 已评分 / 已发布 */
public enum ScoreStatus implements StateMachine.Labeled {
    CALCULATED("已计算"),
    SCORED("已评分"),
    PUBLISHED("已发布");

    private final String label;

    ScoreStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
