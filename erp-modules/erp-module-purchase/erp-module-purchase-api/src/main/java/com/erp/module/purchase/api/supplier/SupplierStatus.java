package com.erp.module.purchase.api.supplier;

/** 供应商状态（需求文档 07）。只有 QUALIFIED 可以下正式采购订单。 */
public enum SupplierStatus {
    POTENTIAL,
    QUALIFIED,
    SUSPENDED,
    ELIMINATED
}
