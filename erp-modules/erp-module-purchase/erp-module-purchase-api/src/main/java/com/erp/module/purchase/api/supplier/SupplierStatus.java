package com.erp.module.purchase.api.supplier;

/**
 * 供应商状态（需求文档 07-01 第 4 节）：潜在 / 准入审批中 / 合格 / 暂停 / 淘汰。
 * 只有 QUALIFIED 可以下正式采购订单。
 */
public enum SupplierStatus {
    POTENTIAL,
    QUALIFIED,
    SUSPENDED,
    ELIMINATED,
    /** 准入审批中 */
    PENDING
}
