package com.erp.module.crm.service;

import com.erp.framework.security.SecurityUtils;
import com.erp.framework.security.UserDataScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按客户的数据范围（需求 03 README 第 3 节，CRM-FU-R04）：联系人、跟进、商机、客户料号、信用能否看到取决于能否看到客户。
 * 客户表本身由 {@code @DataScope} 过滤；子数据用本类生成的子查询过滤（数值来自当前用户的数据范围，不含外部输入）。
 */
public final class CrmScope {

    private CrmScope() {
    }

    /** 当前用户可见客户的 ID 子查询；不受限时返回 null */
    public static String visibleCustomerIds() {
        UserDataScope s = SecurityUtils.currentDataScope();
        if (s.all()) return null;
        List<String> parts = new ArrayList<>();
        if (!s.orgIds().isEmpty()) parts.add("org_id IN (" + join(s.orgIds()) + ")");
        if (!s.deptIds().isEmpty()) parts.add("dept_id IN (" + join(s.deptIds()) + ")");
        Long me = SecurityUtils.getLoginUserIdOrNull();
        if (s.self() && me != null) parts.add("owner_id = " + me);
        String cond = parts.isEmpty() ? "1 = 0" : String.join(" OR ", parts);
        return "SELECT id FROM crm_customer WHERE deleted = 0 AND (" + cond + ")";
    }

    private static String join(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
