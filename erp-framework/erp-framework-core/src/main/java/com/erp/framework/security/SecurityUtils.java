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

    public static Long getLoginUserIdOrNull() {
        LoginUser user = getLoginUserOrNull();
        return user == null ? null : user.id();
    }
}
