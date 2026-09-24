package com.erp.common.result;

import com.erp.common.exception.ErrorCode;
import com.erp.common.exception.GlobalErrorCodes;

/**
 * 统一响应体。code = 0 表示成功，其余为错误码。
 */
public record CommonResult<T>(int code, String msg, T data) {

    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(GlobalErrorCodes.SUCCESS.code(), GlobalErrorCodes.SUCCESS.message(), data);
    }

    public static CommonResult<Void> success() {
        return success(null);
    }

    public static <T> CommonResult<T> error(int code, String msg) {
        return new CommonResult<>(code, msg, null);
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        return error(errorCode.code(), errorCode.message());
    }

    public boolean isSuccess() {
        return code == GlobalErrorCodes.SUCCESS.code();
    }
}
