package com.erp.module.crm.api.customer;

/** 客户状态（需求 03-01 第 4 节）。 */
public enum CustomerStatus {
    /** 潜在客户：可以询价、报价、样品，不能下正式订单 */
    PROSPECT,
    /** 转正式审批中：同潜在客户 */
    PENDING,
    /** 正式客户：可下销售订单、出货 */
    ACTIVE,
    /** 停用：不能报价、下单；在途订单可以出货（提示） */
    DISABLED,
    /** 黑名单：不能报价、下单、出货 */
    BLACKLIST
}
