package com.erp.module.fx.api;

import com.erp.common.exception.ErrorCode;

/** 实时汇率模块错误码，号段 1_016_000_000 ~ 1_016_999_999。 */
public interface FxErrorCodes {

    ErrorCode FETCH_FAILED = new ErrorCode(1_016_001_000, "获取中国银行汇率失败：{}");
    ErrorCode FETCH_BUSY = new ErrorCode(1_016_001_001, "正在获取汇率，请稍后再试");
}
