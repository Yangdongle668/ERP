package com.erp.module.purchase.service;

import com.erp.common.statemachine.StateMachine;

/** 资材单据动作（在通用动作基础上增加恢复执行、供应商确认等） */
public enum PurAction implements StateMachine.Labeled {
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审核"),
    UNAPPROVE("反审核"),
    START("开始执行"),
    COMPLETE("完成"),
    REOPEN("恢复执行"),
    CLOSE("关闭"),
    VOID("作废"),
    CONFIRM("供应商确认"),
    UNCONFIRM("取消确认");

    private final String label;

    PurAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
