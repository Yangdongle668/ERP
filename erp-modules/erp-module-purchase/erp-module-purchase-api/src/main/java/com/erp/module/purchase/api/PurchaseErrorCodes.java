package com.erp.module.purchase.api;

import com.erp.common.exception.ErrorCode;

/** 资材模块错误码，号段 1_007_000_000 ~ 1_007_999_999。 */
public interface PurchaseErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode PURCHASE_PLACEHOLDER = new ErrorCode(1_007_000_000, "资材模块错误");
}
