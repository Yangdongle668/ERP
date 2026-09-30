package com.erp.framework.security;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

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

    /**
     * 以指定用户身份执行（定时任务代表用户生成报表等）：执行期间的权限判断与数据范围都按该用户，结束后恢复原上下文。
     * 用户由调用方通过 {@link LoginUserLoader} 加载。
     */
    public static <T> T runAs(LoginUser user, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        SecurityContextHolder.setContext(ctx);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public static Long getLoginUserIdOrNull() {
        LoginUser user = getLoginUserOrNull();
        return user == null ? null : user.id();
    }
}
