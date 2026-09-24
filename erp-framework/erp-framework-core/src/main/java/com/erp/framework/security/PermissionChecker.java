package com.erp.framework.security;

import org.springframework.stereotype.Component;

/**
 * 接口权限判断，在 Controller 上使用：
 * <pre>{@code @PreAuthorize("@ss.has('eng:material:query')")}</pre>
 */
@Component("ss")
public class PermissionChecker {

    public boolean has(String permission) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        return user != null && user.hasPermission(permission);
    }

    public boolean hasAny(String... permissions) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            return false;
        }
        for (String p : permissions) {
            if (user.hasPermission(p)) {
                return true;
            }
        }
        return false;
    }
}
