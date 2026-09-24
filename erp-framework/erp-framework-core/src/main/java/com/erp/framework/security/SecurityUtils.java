package com.erp.framework.security;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 获取当前登录用户的工具类。 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof LoginUser user ? user : null;
    }

    public static LoginUser getLoginUser() {
        LoginUser user = getLoginUserOrNull();
        if (user == null) {
            throw new BizException(GlobalErrorCodes.UNAUTHORIZED);
        }
        return user;
    }

    /** 当前用户的数据范围；未登录（如定时任务）时为全部 */
    public static UserDataScope currentDataScope() {
        LoginUser user = getLoginUserOrNull();
        if (user == null || user.isSuperAdmin()) {
            return UserDataScope.ALL;
        }
        return user.dataScope();
    }

    public static Long getLoginUserIdOrNull() {
        LoginUser user = getLoginUserOrNull();
        return user == null ? null : user.id();
    }
}
