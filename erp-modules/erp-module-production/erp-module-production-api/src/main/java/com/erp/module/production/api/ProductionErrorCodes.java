package com.erp.module.production.api;

import com.erp.common.exception.ErrorCode;

/** 生产模块错误码，号段 1_009_000_000 ~ 1_009_999_999。 */
public interface ProductionErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode PRODUCTION_PLACEHOLDER = new ErrorCode(1_009_000_000, "生产模块错误");
}
