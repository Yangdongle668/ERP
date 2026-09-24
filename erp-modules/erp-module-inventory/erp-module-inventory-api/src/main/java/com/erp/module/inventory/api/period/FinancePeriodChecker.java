package com.erp.module.inventory.api.period;

/** 财务模块实现：财务期间已结账（成本已锁定）时库存不能反结账（INV-PRD 4.3） */
@FunctionalInterface
public interface FinancePeriodChecker {

    boolean isClosed(String period);
}
