package com.erp.module.sales.service;

import com.erp.common.statemachine.StateMachine;

/** 销售单据动作 */
public enum SalAction implements StateMachine.Labeled {
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
    PUBLISH("发布"),
    ASSIGN("分派"),
    COST("核算完成"),
    QUOTE("生成报价"),
    SEND("发送"),
    WIN("成交"),
    LOSE("未成交"),
    EXPIRE("过期"),
    REVISE("修订");

    private final String label;

    SalAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
