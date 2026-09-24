package com.erp.module.inventory.service.doc;

import com.erp.common.statemachine.StateMachine;

/** 盘点状态（需求 08-06 第 2、3 节）：草稿 → 盘点中 → 已提交 → 已审核；草稿、盘点中可作废；驳回回到盘点中 */
public enum CountStatus implements StateMachine.Labeled {
    DRAFT("草稿"), COUNTING("盘点中"), SUBMITTED("已提交"), APPROVED("已审核"), VOIDED("已作废");

    private final String label;

    CountStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    public enum Action implements StateMachine.Labeled {
        GENERATE("生成盘点表"), SUBMIT("提交"), APPROVE("审核"), REJECT("驳回"), VOID("作废");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public static final StateMachine<CountStatus, Action> MACHINE = StateMachine.builder(CountStatus.class, Action.class)
            .transition(DRAFT, Action.GENERATE, COUNTING)
            .transition(COUNTING, Action.SUBMIT, SUBMITTED)
            .transition(SUBMITTED, Action.APPROVE, APPROVED)
            .transition(SUBMITTED, Action.REJECT, COUNTING)
            .transition(DRAFT, Action.VOID, VOIDED)
            .transition(COUNTING, Action.VOID, VOIDED)
            .build();
}
