package com.erp.common.util;

import com.erp.common.exception.BizException;
import com.erp.common.exception.ErrorCode;

import java.util.Collection;

/** 业务断言：条件不满足时抛出 {@link BizException}。 */
public final class BizAssert {

    private BizAssert() {
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, Object... args) {
        if (!condition) {
            throw BizException.of(errorCode, args);
        }
    }

    public static <T> T notNull(T obj, ErrorCode errorCode, Object... args) {
        if (obj == null) {
            throw BizException.of(errorCode, args);
        }
        return obj;
    }

    public static void notEmpty(Collection<?> collection, ErrorCode errorCode, Object... args) {
        if (collection == null || collection.isEmpty()) {
            throw BizException.of(errorCode, args);
        }
    }
}
