package com.erp.module.inventory.api.doc;

/** 出库类型（需求 08-仓库/04-出库单 第 1.1 节）。 */
public enum StockOutType {
    PRODUCTION_ISSUE, OUTSOURCE_ISSUE, SALES_OUT, PURCHASE_RETURN, OTHER_OUT, COUNT_LOSS
}
