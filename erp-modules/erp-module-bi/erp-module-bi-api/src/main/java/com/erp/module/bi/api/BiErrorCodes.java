package com.erp.module.bi.api;

import com.erp.common.exception.ErrorCode;

/** BI/AI模块错误码，号段 1_013_000_000 ~ 1_013_999_999。 */
public interface BiErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode BI_PLACEHOLDER = new ErrorCode(1_013_000_000, "BI/AI模块错误");
}
