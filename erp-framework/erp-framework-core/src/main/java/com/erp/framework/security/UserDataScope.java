package com.erp.framework.security;

import java.util.Set;

/**
 * 当前用户的数据范围（01-03 角色与权限 1.2），由多个角色的数据范围取并集后得到。
 *
 * <p>业务查询条件：{@code all} 为 true 时不限制；否则满足以下任一条件即可见：
 * <ul>
 *   <li>单据 org_id 在 {@link #orgIds()} 中（本公司）；</li>
 *   <li>单据 dept_id 在 {@link #deptIds()} 中（本部门 / 本部门及下级 / 自定义部门，已展开为部门 ID 集合）；</li>
 *   <li>{@link #self()} 为 true 且单据负责人（owner_id 或 created_by）是当前用户。</li>
 * </ul>
 * 三个条件都不满足（例如集合为空且 self 为 false）时看不到任何数据。
 */
public record UserDataScope(boolean all, Set<Long> orgIds, Set<Long> deptIds, boolean self) {

    public static final UserDataScope ALL = new UserDataScope(true, Set.of(), Set.of(), false);
    public static final UserDataScope SELF_ONLY = new UserDataScope(false, Set.of(), Set.of(), true);

    public UserDataScope {
        orgIds = orgIds == null ? Set.of() : Set.copyOf(orgIds);
        deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
    }
}
