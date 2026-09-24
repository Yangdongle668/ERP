package com.erp.module.engineering.api.bom;

/** 发料方式：领料 / 倒冲（报工时自动扣料） */
public enum IssueMethod {
    PICK("领料"), BACKFLUSH("倒冲");

    private final String label;

    IssueMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
