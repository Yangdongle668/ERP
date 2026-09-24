package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.LoginUser;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.controller.vo.RoleVOs.MemberResp;
import com.erp.module.system.controller.vo.RoleVOs.RoleDetail;
import com.erp.module.system.controller.vo.RoleVOs.RoleQuery;
import com.erp.module.system.controller.vo.RoleVOs.RoleResp;
import com.erp.module.system.controller.vo.RoleVOs.RoleSave;
import com.erp.module.system.controller.vo.RoleVOs.RoleSimple;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.dal.mapper.UserRoleMapper;
import com.erp.module.system.enums.DataScopeType;
import com.erp.module.system.service.support.SystemCaches;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色与权限（01-03）。 */
@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final OrgService orgService;
    private final SystemCaches caches;

    public RoleService(RoleMapper roleMapper, UserRoleMapper userRoleMapper, UserMapper userMapper, PermissionService permissionService,
                       OrgService orgService, SystemCaches caches) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
        this.orgService = orgService;
        this.caches = caches;
    }

    public PageResult<RoleResp> page(RoleQuery q) {
        LambdaQueryWrapper<RoleDO> w = new LambdaQueryWrapper<RoleDO>()
                .eq(StringUtils.hasText(q.getStatus()), RoleDO::getStatus, StringUtils.hasText(q.getStatus()) ? EnableStatus.valueOf(q.getStatus()) : null)
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(RoleDO::getCode, q.getKeyword().trim()).or().like(RoleDO::getName, q.getKeyword().trim()))
                .orderByAsc(RoleDO::getSort).orderByAsc(RoleDO::getId);
        Map<Long, Long> counts = userRoleMapper.countEnabledUsersGroupByRole().stream()
                .collect(Collectors.toMap(UserMapper.IdCount::id, UserMapper.IdCount::cnt));
        return roleMapper.selectPage(q, w).map(r -> new RoleResp(r.getId(), r.getCode(), r.getName(), r.getDataScope().name(),
                Boolean.TRUE.equals(r.getBuiltin()), counts.getOrDefault(r.getId(), 0L).intValue(), r.getSort(), r.getStatus().name(),
                r.getRemark(), r.getVersion()));
    }

    public List<RoleSimple> simple() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getStatus, EnableStatus.ENABLED)
                        .orderByAsc(RoleDO::getSort).orderByAsc(RoleDO::getId))
                .stream().map(r -> new RoleSimple(r.getId(), r.getCode(), r.getName())).toList();
    }

    public RoleDetail get(Long id) {
        RoleDO r = getRole(id);
        return new RoleDetail(r.getId(), r.getCode(), r.getName(), r.getDataScope().name(), roleMapper.selectDataDeptIds(id),
                Boolean.TRUE.equals(r.getBuiltin()), r.getSort(), r.getStatus().name(), r.getRemark(), r.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSave req) {
        String code = req.code().trim().toUpperCase();
        checkUnique(code, req.name().trim(), null);
        RoleDO r = new RoleDO();
        r.setCode(code);
        r.setName(req.name().trim());
        r.setDataScope(DataScopeType.valueOf(req.dataScope()));
        r.setSort(req.sort());
        r.setRemark(req.remark());
        r.setBuiltin(false);
        r.setStatus(EnableStatus.ENABLED);
        try {
            roleMapper.insert(r);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.ROLE_CODE_DUPLICATE, code);
        }
        saveDataDepts(r, req.customDeptIds());
        return r.getId();
    }

    /** 修改（编码创建后不可改）；内置角色不能修改（R03） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSave req) {
        RoleDO r = getEditable(id);
        checkUnique(r.getCode(), req.name().trim(), id);
        r.setName(req.name().trim());
        r.setDataScope(DataScopeType.valueOf(req.dataScope()));
        r.setSort(req.sort());
        r.setRemark(req.remark());
        r.setVersion(req.version());
        roleMapper.updateByIdOrFail(r);
        saveDataDepts(r, req.customDeptIds());
        caches.clearLoginUsers();
    }

    private void saveDataDepts(RoleDO r, List<Long> deptIds) {
        roleMapper.deleteDataDepts(r.getId());
        if (r.getDataScope() != DataScopeType.CUSTOM) return;
        Set<Long> ids = new LinkedHashSet<>(deptIds == null ? List.of() : deptIds);
        if (ids.isEmpty()) throw new BizException(SystemErrorCodes.ROLE_CUSTOM_DEPT_REQUIRED);
        for (Long d : ids) {
            if (orgService.get(d).isEmpty()) throw new BizException(SystemErrorCodes.ORG_NOT_EXISTS);
            roleMapper.insertDataDept(r.getId(), d);
        }
    }

    /** 复制：包括功能权限和数据范围（名称加“-副本”，编码加 _COPY） */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        RoleDO src = getRole(id);
        String code = src.getCode() + "_COPY";
        String name = src.getName() + "-副本";
        for (int i = 2; roleMapper.selectByCode(code) != null || roleMapper.selectByName(name) != null; i++) {
            code = src.getCode() + "_COPY" + i;
            name = src.getName() + "-副本" + i;
        }
        if (code.length() > 32) code = code.substring(code.length() - 32);
        RoleDO r = new RoleDO();
        r.setCode(code);
        r.setName(name.length() > 32 ? name.substring(0, 32) : name);
        r.setDataScope(src.getDataScope());
        r.setSort(src.getSort() + 1);
        r.setRemark(src.getRemark());
        r.setBuiltin(false);
        r.setStatus(EnableStatus.ENABLED);
        roleMapper.insert(r);
        for (Long d : roleMapper.selectDataDeptIds(id)) roleMapper.insertDataDept(r.getId(), d);
        for (String p : roleMapper.selectPermissions(id)) {
            if (!LoginUser.ALL_PERMISSION.equals(p)) roleMapper.insertPermission(r.getId(), p);
        }
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        RoleDO r = getEditable(id);
        if (r.getStatus() == EnableStatus.ENABLED) return;
        r.setStatus(EnableStatus.ENABLED);
        roleMapper.updateByIdOrFail(r);
        caches.clearLoginUsers();
    }

    /** 停用（R04）：停用后每个启用用户至少还有一个启用角色，否则列出受影响的用户 */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        RoleDO r = getEditable(id);
        if (r.getStatus() == EnableStatus.DISABLED) return;
        List<String> orphans = new ArrayList<>();
        for (UserDO u : userMapper.selectByRole(id)) {
            if (u.getStatus() == EnableStatus.ENABLED && userRoleMapper.countOtherEnabledRoles(u.getId(), id) == 0) orphans.add(u.getRealName());
        }
        if (!orphans.isEmpty()) throw BizException.of(SystemErrorCodes.ROLE_DISABLE_ORPHAN_USERS, String.join("、", orphans));
        r.setStatus(EnableStatus.DISABLED);
        roleMapper.updateByIdOrFail(r);
        caches.clearLoginUsers();
    }

    /** 删除（R05）：角色下存在用户（含停用）时不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getEditable(id);
        long n = userRoleMapper.countByRole(id);
        if (n > 0) throw BizException.of(SystemErrorCodes.ROLE_HAS_USERS, n);
        roleMapper.deletePermissions(id);
        roleMapper.deleteDataDepts(id);
        roleMapper.deleteById(id);
    }

    // ==================== 功能权限 ====================

    public List<String> permissions(Long id) {
        getRole(id);
        return roleMapper.selectPermissions(id);
    }

    /** 覆盖保存（R06：只能授予存在且有效的权限点，自动补齐依赖）；清除相关用户的权限缓存（R07） */
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long id, List<String> codes) {
        getEditable(id);
        Set<String> resolved = permissionService.resolveGrant(codes);
        roleMapper.deletePermissions(id);
        for (String p : resolved) roleMapper.insertPermission(id, p);
        caches.clearLoginUsers();
    }

    // ==================== 成员 ====================

    public List<MemberResp> members(Long id) {
        getRole(id);
        return userMapper.selectByRole(id).stream()
                .map(u -> new MemberResp(u.getId(), u.getUsername(), u.getRealName(), orgService.shortPathName(u.getDeptId()), u.getStatus().name()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long id, List<Long> userIds) {
        RoleDO r = getRole(id);
        if (r.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.ROLE_DISABLED, r.getName());
        if (Boolean.TRUE.equals(r.getBuiltin())) throw new BizException(SystemErrorCodes.USER_SUPER_ADMIN_ROLE_ONLY);
        Set<Long> existing = new java.util.HashSet<>(userRoleMapper.selectUserIds(id));
        for (Long uid : new LinkedHashSet<>(userIds)) {
            if (userMapper.selectById(uid) == null) throw new BizException(SystemErrorCodes.USER_NOT_EXISTS);
            if (!existing.contains(uid)) {
                userRoleMapper.insert(uid, id);
                caches.evictLoginUser(uid);
            }
        }
    }

    /** 移除成员：用户至少保留一个角色（SYS-USR-R05） */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long id, Long userId) {
        RoleDO r = getRole(id);
        if (Boolean.TRUE.equals(r.getBuiltin())) throw new BizException(SystemErrorCodes.ROLE_BUILTIN);
        UserDO u = userMapper.selectById(userId);
        if (u == null) throw new BizException(SystemErrorCodes.USER_NOT_EXISTS);
        if (userRoleMapper.selectRoleIds(userId).size() <= 1) throw BizException.of(SystemErrorCodes.ROLE_MEMBER_LAST_ROLE, u.getRealName());
        userRoleMapper.delete(userId, id);
        caches.evictLoginUser(userId);
    }

    // ==================== 工具 ====================

    private void checkUnique(String code, String name, Long excludeId) {
        RoleDO byCode = roleMapper.selectByCode(code);
        if (byCode != null && !byCode.getId().equals(excludeId)) throw BizException.of(SystemErrorCodes.ROLE_CODE_DUPLICATE, code);
        RoleDO byName = roleMapper.selectByName(name);
        if (byName != null && !byName.getId().equals(excludeId)) throw BizException.of(SystemErrorCodes.ROLE_NAME_DUPLICATE, name);
    }

    private RoleDO getRole(Long id) {
        RoleDO r = roleMapper.selectById(id);
        if (r == null) throw new BizException(SystemErrorCodes.ROLE_NOT_EXISTS);
        return r;
    }

    private RoleDO getEditable(Long id) {
        RoleDO r = getRole(id);
        if (Boolean.TRUE.equals(r.getBuiltin())) throw new BizException(SystemErrorCodes.ROLE_BUILTIN);
        return r;
    }
}
