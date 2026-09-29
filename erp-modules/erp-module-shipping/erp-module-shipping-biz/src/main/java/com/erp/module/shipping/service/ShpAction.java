package com.erp.module.shipping.service;

import com.erp.common.statemachine.StateMachine;

/** 出货单据动作 */
public enum ShpAction implements StateMachine.Labeled {
    CREATE("新建"),
    SAVE("保存"),
    SUBMIT("提交"),
    WITHDRAW("撤回"),
    REJECT("驳回"),
    APPROVE("审核"),
    UNAPPROVE("反审核"),
    START_PICK("开始拣货"),
    COMPLETE_PICK("完成拣货"),
    CANCEL("取消"),
    PACK("完成装箱"),
    UNPACK("修改装箱"),
    REQUEST_OQC("申请 OQC"),
    OQC_PASS("OQC 合格"),
    OQC_REJECT("OQC 不合格"),
    SHIP_ALL("全部出货"),
    UNSHIP("出货冲回"),
    CLOSE("关闭"),
    VOID("作废"),
    CONFIRM_OUT("确认出库"),
    OUT_REJECTED("出库单退回"),
    REVERSE("反确认出库"),
    COMPLETE("完成");

    private final String label;

    ShpAction(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
