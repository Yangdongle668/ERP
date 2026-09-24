package com.erp.module.quality.api;

import com.erp.common.exception.ErrorCode;

/** 品质模块错误码，号段 1_010_000_000 ~ 1_010_999_999。 */
public interface QualityErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode QUALITY_PLACEHOLDER = new ErrorCode(1_010_000_000, "品质模块错误");
}
