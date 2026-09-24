package com.erp.module.crm.api;

import com.erp.common.exception.ErrorCode;

/** CRM模块错误码，号段 1_003_000_000 ~ 1_003_999_999。 */
public interface CrmErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode CRM_PLACEHOLDER = new ErrorCode(1_003_000_000, "CRM模块错误");
}
