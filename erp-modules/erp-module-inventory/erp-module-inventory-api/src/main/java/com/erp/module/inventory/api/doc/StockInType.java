package com.erp.module.inventory.api.doc;

/** 入库类型（需求 08-仓库/03-入库单 第 1.1 节）。 */
public enum StockInType {
    PURCHASE_IN, OUTSOURCE_IN, OUTSOURCE_RETURN, PRODUCTION_IN, PRODUCTION_RETURN,
    SALES_RETURN, OTHER_IN, COUNT_GAIN, OPENING
}
