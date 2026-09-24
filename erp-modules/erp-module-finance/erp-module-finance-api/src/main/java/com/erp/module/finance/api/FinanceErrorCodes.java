package com.erp.module.finance.api;

import com.erp.common.exception.ErrorCode;

/** 财务模块错误码，号段 1_012_000_000 ~ 1_012_999_999。 */
public interface FinanceErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode FINANCE_PLACEHOLDER = new ErrorCode(1_012_000_000, "财务模块错误");
}
