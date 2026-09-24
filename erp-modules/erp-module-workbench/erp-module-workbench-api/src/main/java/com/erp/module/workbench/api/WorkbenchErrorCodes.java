package com.erp.module.workbench.api;

import com.erp.common.exception.ErrorCode;

/** 工作台模块错误码，号段 1_002_000_000 ~ 1_002_999_999。 */
public interface WorkbenchErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode WORKBENCH_PLACEHOLDER = new ErrorCode(1_002_000_000, "工作台模块错误");
}
