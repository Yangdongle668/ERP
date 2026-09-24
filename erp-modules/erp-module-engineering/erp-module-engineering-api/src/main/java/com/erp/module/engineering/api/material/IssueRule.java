package com.erp.module.engineering.api.material;

/** 出库批次推荐规则：先进先出 / 先到期先出 */
public enum IssueRule {
    FIFO("先进先出"), FEFO("先到期先出");

    private final String label;

    IssueRule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
