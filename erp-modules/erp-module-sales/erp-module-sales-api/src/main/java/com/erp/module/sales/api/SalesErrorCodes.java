package com.erp.module.sales.api;

import com.erp.common.exception.ErrorCode;

/** 销售模块错误码，号段 1_004_000_000 ~ 1_004_999_999。 */
public interface SalesErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode SALES_PLACEHOLDER = new ErrorCode(1_004_000_000, "销售模块错误");
}
