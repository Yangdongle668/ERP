package com.erp.module.system.api.paymentterm;

/** 扩展点：付款条件是否被客户、供应商或单据引用（SYS-PT-R03）。 */
public interface PaymentTermReferenceChecker {

    boolean isReferenced(Long termId);
}
