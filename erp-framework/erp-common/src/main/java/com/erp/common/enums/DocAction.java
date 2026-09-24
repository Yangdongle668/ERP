package com.erp.common.enums;

import com.erp.common.statemachine.StateMachine;

/** 单据通用动作。 */
public enum DocAction implements StateMachine.Labeled {
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审核"),
    UNAPPROVE("反审核"),
    START("开始执行"),
    COMPLETE("完成"),
    CLOSE("关闭"),
    VOID("作废");

    private final String label;

    DocAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
