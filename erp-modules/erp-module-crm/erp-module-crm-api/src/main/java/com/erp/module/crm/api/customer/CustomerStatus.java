package com.erp.module.crm.api.customer;

/** 客户状态（需求文档 03 第 6 节）。 */
public enum CustomerStatus {
    /** 潜在客户：只能用于 RFQ、报价、样品 */
    PROSPECT,
    /** 正式客户：可下销售订单 */
    ACTIVE,
    DISABLED,
    BLACKLIST
}
