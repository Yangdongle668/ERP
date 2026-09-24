package com.erp.framework.security;

import java.util.Set;

/**
 * 当前登录用户。
 *
 * @param permissions        权限标识集合，如 {@code eng:material:query}；包含 {@link #ALL_PERMISSION} 表示超级管理员
 * @param tokenVersion       令牌版本：修改密码、重置密码、强制下线、停用时递增，令牌中的版本不一致即失效
 * @param mustChangePassword 首次登录或密码过期，只能访问修改密码等少数接口
 * @param dataScope          数据范围（多个角色取并集）
 */
public record LoginUser(Long id, String username, String realName, Long orgId, Long deptId, Set<String> permissions,
                        int tokenVersion, boolean mustChangePassword, UserDataScope dataScope) {

    public static final String ALL_PERMISSION = "*";

    public LoginUser {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        dataScope = dataScope == null ? UserDataScope.SELF_ONLY : dataScope;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(ALL_PERMISSION) || permissions.contains(permission);
    }

    public boolean isSuperAdmin() {
        return permissions.contains(ALL_PERMISSION);
    }
}
