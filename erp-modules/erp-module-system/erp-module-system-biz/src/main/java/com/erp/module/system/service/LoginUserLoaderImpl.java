package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.LoginUserLoader;
import com.erp.framework.security.UserDataScope;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.dal.mapper.UserRoleMapper;
import com.erp.module.system.enums.DataScopeType;
import com.erp.module.system.service.support.SystemCaches;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 为框架提供登录用户：权限、令牌版本、是否需要改密、数据范围（多个角色取并集，01-03 1.2）。
 * 结果缓存在 {@link SystemCaches#LOGIN_USER}，角色、权限、组织、用户变更时清除。
 */
@Service
public class LoginUserLoaderImpl implements LoginUserLoader {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final OrgService orgService;
    private final PasswordPolicyService passwordPolicy;
    private final SystemCaches caches;

    public LoginUserLoaderImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper, OrgService orgService,
                               PasswordPolicyService passwordPolicy, SystemCaches caches) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.orgService = orgService;
        this.passwordPolicy = passwordPolicy;
        this.caches = caches;
    }

    @Override
    public Optional<LoginUser> load(Long userId) {
        LoginUser user = caches.getNullable(SystemCaches.LOGIN_USER, userId, () -> build(userId).orElse(null));
        return Optional.ofNullable(user);
    }

    private Optional<LoginUser> build(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != EnableStatus.ENABLED) return Optional.empty();
        Set<String> permissions = userMapper.selectPermissions(userId);
        List<Long> roleIds = userRoleMapper.selectRoleIds(userId);
        List<RoleDO> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .in(RoleDO::getId, roleIds).eq(RoleDO::getStatus, EnableStatus.ENABLED));
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword()) || passwordPolicy.isExpired(user);
        return Optional.of(new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getOrgId(), user.getDeptId(),
                permissions, user.getTokenVersion() == null ? 0 : user.getTokenVersion(), mustChange,
                dataScope(user, roles, permissions.contains(LoginUser.ALL_PERMISSION))));
    }

    /** 数据范围取并集：任一角色为“全部”即全部；本公司 → 公司 ID；本部门（及下级）→ 部门 ID；自定义 → 角色指定部门 */
    UserDataScope dataScope(UserDO user, List<RoleDO> roles, boolean superAdmin) {
        if (superAdmin) return UserDataScope.ALL;
        Set<Long> orgIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        boolean self = false;
        List<Long> myDepts = new ArrayList<>();
        if (user.getDeptId() != null) myDepts.add(user.getDeptId());
        myDepts.addAll(userRoleMapper.selectPartDeptIds(user.getId()));
        for (RoleDO r : roles) {
            DataScopeType t = r.getDataScope() == null ? DataScopeType.SELF : r.getDataScope();
            switch (t) {
                case ALL -> {
                    return UserDataScope.ALL;
                }
                case COMPANY -> {
                    if (user.getOrgId() != null) orgIds.add(user.getOrgId());
                }
                case DEPT_AND_CHILD -> myDepts.forEach(d -> deptIds.addAll(orgService.getSelfAndChildrenIds(d)));
                case DEPT -> deptIds.addAll(myDepts);
                case SELF -> self = true;
                case CUSTOM -> deptIds.addAll(roleMapper.selectDataDeptIds(r.getId()));
            }
        }
        // 自己的数据始终可见（单据负责人为自己）
        self = true;
        return new UserDataScope(false, orgIds, deptIds, self);
    }
}
