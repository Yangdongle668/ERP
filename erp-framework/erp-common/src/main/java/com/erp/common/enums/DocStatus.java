package com.erp.common.enums;

import com.erp.common.statemachine.StateMachine;

/** 单据通用状态（需求文档 00 第 4.2 节）。数据库存储枚举名。 */
public enum DocStatus implements StateMachine.Labeled {
    DRAFT("草稿"),
    PENDING_APPROVAL("待审批"),
    APPROVED("已审核"),
    IN_PROGRESS("执行中"),
    COMPLETED("已完成"),
    CLOSED("已关闭"),
    VOIDED("已作废");

    private final String label;

    DocStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    /** 只有草稿可以编辑、删除。 */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
