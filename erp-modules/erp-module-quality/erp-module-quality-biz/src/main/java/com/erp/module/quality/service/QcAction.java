package com.erp.module.quality.service;

import com.erp.common.statemachine.StateMachine;

/** 品质单据动作 */
public enum QcAction implements StateMachine.Labeled {
    CREATE("新建"),
    SAVE("保存"),
    START("开始"),
    JUDGE("判定"),
    TO_MRB("提交 MRB"),
    MRB_JUDGE("MRB 判定"),
    MRB_SORT("MRB 挑选"),
    MRB_BACK("MRB 退回"),
    HANDLE("检验调拨完成"),
    REJUDGE("重判"),
    CANCEL("取消"),
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审核"),
    VOID("作废"),
    CLOSE("关闭"),
    REPLY("回复"),
    SEND("发出"),
    START_VERIFY("开始验证"),
    TO_VERIFY("待验证"),
    VERIFY_OK("验证有效"),
    VERIFY_FAIL("验证无效"),
    ACTIVATE("生效"),
    OBSOLETE("作废"),
    NEW_VERSION("新版本"),
    COMPLETE_STEP("完成步骤"),
    HANDLING("登记处理结果"),
    DONE("处置完成");

    private final String label;

    QcAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
