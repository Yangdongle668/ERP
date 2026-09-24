package com.erp.module.inventory.api;

import com.erp.common.exception.ErrorCode;

/** 仓库模块错误码，号段 1_008_000_000 ~ 1_008_999_999。 */
public interface InventoryErrorCodes {

    ErrorCode STOCK_NOT_ENOUGH = new ErrorCode(1_008_001_000, "物料【{}】在仓库【{}】可用库存不足，需要 {}，可用 {}");
    ErrorCode WAREHOUSE_NOT_AVAILABLE = new ErrorCode(1_008_001_001, "【{}】是不可用仓，不能执行该出库操作");
    ErrorCode DUPLICATE_POSTING = new ErrorCode(1_008_001_002, "单据【{}】已过账，不能重复过账");
    ErrorCode PERIOD_CLOSED = new ErrorCode(1_008_001_003, "库存期间【{}】已结账");
}
