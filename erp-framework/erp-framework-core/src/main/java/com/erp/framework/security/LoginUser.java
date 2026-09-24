package com.erp.framework.security;

import java.util.Set;

/**
 * 当前登录用户。
 *
 * @param permissions 权限标识集合，如 {@code eng:material:query}；包含 {@link #ALL_PERMISSION} 表示超级管理员
 */
public record LoginUser(Long id, String username, String realName, Long orgId, Long deptId, Set<String> permissions) {

    public static final String ALL_PERMISSION = "*";

    public boolean hasPermission(String permission) {
        return permissions != null && (permissions.contains(ALL_PERMISSION) || permissions.contains(permission));
    }
}
