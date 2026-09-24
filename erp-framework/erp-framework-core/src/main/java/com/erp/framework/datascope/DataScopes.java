package com.erp.framework.datascope;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.framework.security.SecurityUtils;
import com.erp.framework.security.UserDataScope;

import java.util.Objects;
import java.util.function.Supplier;

/** 数据权限工具：临时跳过过滤、校验单条数据是否在当前用户范围内。 */
public final class DataScopes {

    private static final ThreadLocal<Integer> IGNORE = ThreadLocal.withInitial(() -> 0);

    private DataScopes() {
    }

    /** 在不过滤数据权限的情况下执行（系统内部校验、统计等），支持嵌套 */
    public static <T> T ignore(Supplier<T> action) {
        IGNORE.set(IGNORE.get() + 1);
        try {
            return action.get();
        } finally {
            int n = IGNORE.get() - 1;
            if (n <= 0) IGNORE.remove();
            else IGNORE.set(n);
        }
    }

    public static void ignore(Runnable action) {
        ignore(() -> {
            action.run();
            return null;
        });
    }

    static boolean isIgnored() {
        return IGNORE.get() > 0;
    }

    /** 当前用户能否看到这条数据 */
    public static boolean visible(Long orgId, Long deptId, Long ownerId) {
        UserDataScope s = SecurityUtils.currentDataScope();
        if (s.all()) return true;
        if (orgId != null && s.orgIds().contains(orgId)) return true;
        if (deptId != null && s.deptIds().contains(deptId)) return true;
        return s.self() && ownerId != null && Objects.equals(ownerId, SecurityUtils.getLoginUserIdOrNull());
    }

    /** 不可见时按“数据不存在”处理，避免泄露数据是否存在 */
    public static void check(Long orgId, Long deptId, Long ownerId, String objectName) {
        if (!visible(orgId, deptId, ownerId)) {
            throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, objectName);
        }
    }
}
