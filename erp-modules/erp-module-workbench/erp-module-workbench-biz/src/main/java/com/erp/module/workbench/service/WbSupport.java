package com.erp.module.workbench.service;

import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 工作台公共支持：当前用户、用户与组织、参数、独立事务 */
@Component
public class WbSupport {

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final ParamApi paramApi;
    private final TransactionTemplate newTx;

    public WbSupport(UserApi userApi, OrgApi orgApi, ParamApi paramApi, PlatformTransactionManager tm) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.paramApi = paramApi;
        this.newTx = new TransactionTemplate(tm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Long currentUser() {
        return SecurityUtils.getLoginUser().id();
    }

    /** 当前用户是否拥有权限；permission 多个用 | 分隔表示任一，为空表示无需权限 */
    public boolean hasPermission(String permission) {
        if (!StringUtils.hasText(permission)) return true;
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        if (u == null) return false;
        return Arrays.stream(permission.split("\\|")).map(String::trim).anyMatch(u::hasPermission);
    }

    public UserApi userApi() {
        return userApi;
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : userApi.list(set);
    }

    public static String name(Map<Long, UserDTO> users, Long id) {
        UserDTO u = id == null ? null : users.get(id);
        return u == null ? null : u.realName();
    }

    public List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
    }

    public Map<Long, OrgDTO> orgs(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : orgApi.list(set);
    }

    public OrgApi orgApi() {
        return orgApi;
    }

    public ParamApi params() {
        return paramApi;
    }

    /** 事件监听在业务事务提交后执行，写入使用独立事务 */
    public void inNewTx(Runnable action) {
        newTx.executeWithoutResult(s -> action.run());
    }

    public static String requireText(String text, String what) {
        if (!StringUtils.hasText(text)) throw BizException.of(WorkbenchErrorCodes.REASON_REQUIRED, what);
        return text.trim();
    }

    public static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
