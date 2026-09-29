package com.erp.module.finance.service;

import com.erp.common.statemachine.StateMachine;

/** 财务单据动作 */
public enum FinAction implements StateMachine.Labeled {
    CREATE("新建"),
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审批通过"),
    CONFIRM("确认"),
    UNCONFIRM("反确认"),
    VOID("作废"),
    PAY("付款"),
    PAY_ALL("付清"),
    UNPAY("付款冲回"),
    UNPAY_ALL("付款全部冲回"),
    CLOSE("关闭");

    private final String label;

    FinAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
