package com.erp.module.crm.service;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** CRM 各功能共用：用户与部门解析、编号、操作日志、字典、参数、权限判断 */
@Component
public class CrmSupport {

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final DictApi dictApi;
    private final ParamApi paramApi;

    public CrmSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, DictApi dictApi, ParamApi paramApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.dictApi = dictApi;
        this.paramApi = paramApi;
    }

    public Long currentUser() {
        return SecurityUtils.getLoginUserIdOrNull();
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : userApi.list(set);
    }

    public static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    public UserDTO user(Long id) {
        return id == null ? null : userApi.get(id).orElse(null);
    }

    public UserApi userApi() {
        return userApi;
    }

    public Map<Long, OrgDTO> orgs(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : orgApi.list(set);
    }

    public Long companyOf(Long deptId, Long fallback) {
        if (deptId == null) return fallback;
        return orgApi.getCompanyOf(deptId).map(OrgDTO::id).orElse(fallback);
    }

    public List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
    }

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public boolean manualCodeAllowed(String rule) {
        return codeRuleApi.isManualAllowed(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public DictApi dict() {
        return dictApi;
    }

    public ParamApi param() {
        return paramApi;
    }

    /** 未登录的后台任务视为有权限 */
    public static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }

    public static boolean canViewCredit() {
        return hasPermission(CrmModuleConfig.CREDIT_FIELD);
    }

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
