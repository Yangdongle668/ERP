package com.erp.module.pmc.api;

import com.erp.common.exception.ErrorCode;

/** PMC模块错误码，号段 1_006_000_000 ~ 1_006_999_999。 */
public interface PmcErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode PMC_PLACEHOLDER = new ErrorCode(1_006_000_000, "PMC模块错误");
}
