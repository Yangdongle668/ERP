package com.erp.module.engineering.service;

import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 第 2 批（工作中心、工艺路线、ECN、项目、样品、工装、认证）共用：名称解析、编号、日志、当前用户 */
@Component
public class EngSupport {

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CustomerApi customerApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialService materialService;

    public EngSupport(UserApi userApi, OrgApi orgApi, CustomerApi customerApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi,
                      MaterialService materialService) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.customerApi = customerApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialService = materialService;
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : userApi.list(set);
    }

    public static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    public String userName(Long id) {
        return id == null ? null : userApi.get(id).map(UserDTO::realName).orElse(null);
    }

    public Map<Long, OrgDTO> orgs(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : orgApi.list(set);
    }

    /** 拥有某权限的启用用户 ID */
    public java.util.List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
    }

    public boolean userExists(Long id) {
        return id != null && userApi.get(id).isPresent();
    }

    public boolean orgExists(Long id) {
        return id != null && orgApi.get(id).isPresent();
    }

    /** 客户名称；CRM 模块尚未实现客户查询时返回空 */
    public Map<Long, String> customers(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) {
            try {
                customerApi.getCustomer(id).map(CustomerDTO::name).ifPresent(n -> map.put(id, n));
            } catch (RuntimeException ignored) {
                // CRM 未实现：只显示 ID
            }
        }
        return map;
    }

    public Map<Long, MaterialDO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : materialService.byIds(set);
    }

    public MaterialDO material(Long id) {
        return materialService.getOrThrow(id);
    }

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public Long currentUser() {
        return SecurityUtils.getLoginUserIdOrNull();
    }

    public static boolean hasPermission(String permission) {
        var u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
