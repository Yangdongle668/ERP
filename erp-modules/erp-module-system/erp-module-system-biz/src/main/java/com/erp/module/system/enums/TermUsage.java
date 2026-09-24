package com.erp.module.system.enums;

/** 付款条件适用范围 */
public enum TermUsage {
    SALES, PURCHASE, BOTH;

    public boolean accepts(String usage) {
        return this == BOTH || name().equals(usage);
    }
}
