package com.erp.module.system.api.currency;

/** 扩展点：业务模块中是否已存在金额数据（修改本位币前检查，SYS-CUR-R03）。 */
public interface CurrencyReferenceChecker {

    boolean hasAmountData();
}
