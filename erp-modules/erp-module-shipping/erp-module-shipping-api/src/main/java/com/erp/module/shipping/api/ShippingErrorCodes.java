package com.erp.module.shipping.api;

import com.erp.common.exception.ErrorCode;

/** 出货模块错误码，号段 1_011_000_000 ~ 1_011_999_999。 */
public interface ShippingErrorCodes {

    /** 占位示例，新增错误码时按号段递增。 */
    ErrorCode SHIPPING_PLACEHOLDER = new ErrorCode(1_011_000_000, "出货模块错误");
}
