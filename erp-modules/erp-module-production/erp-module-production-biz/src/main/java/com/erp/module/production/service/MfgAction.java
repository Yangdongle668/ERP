package com.erp.module.production.service;

import com.erp.common.statemachine.StateMachine;

/** 生产单据动作 */
public enum MfgAction implements StateMachine.Labeled {
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审核"),
    UNAPPROVE("反审核"),
    RELEASE("下达"),
    UNRELEASE("撤销下达"),
    START("开始生产"),
    SUSPEND("暂停"),
    RESUME("恢复"),
    RESUME_RUN("恢复"),
    COMPLETE("完成"),
    REOPEN("恢复执行"),
    RESET("回到未开始"),
    CLOSE("关闭"),
    VOID("作废"),
    CANCEL("取消"),
    STOCK("入库"),
    UNSTOCK("入库冲销"),
    JUDGE("FQC 判定"),
    UNJUDGE("判定撤销");

    private final String label;

    MfgAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
