package com.erp.module.finance.api.query;

/** 会计期间查询（仓库反结账校验等） */
public interface FinPeriodApi {

    /** @param period yyyyMM */
    boolean isClosed(String period);
}
