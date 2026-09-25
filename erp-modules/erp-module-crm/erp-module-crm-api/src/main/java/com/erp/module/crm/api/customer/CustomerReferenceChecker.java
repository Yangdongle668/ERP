package com.erp.module.crm.api.customer;

/**
 * 客户引用检查扩展点（需求 03-01 R09）：销售（RFQ、报价、订单）、研发工程（样品）等模块实现，
 * 有业务数据的潜在客户不能删除。未实现的模块视为没有引用。
 */
public interface CustomerReferenceChecker {

    boolean hasBusinessData(Long customerId);
}
