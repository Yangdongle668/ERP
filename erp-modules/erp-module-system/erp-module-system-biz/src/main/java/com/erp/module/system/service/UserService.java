package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.controller.vo.UserVOs.BatchResult;
import com.erp.module.system.controller.vo.UserVOs.CreateResult;
import com.erp.module.system.controller.vo.UserVOs.ResetPasswordReq;
import com.erp.module.system.controller.vo.UserVOs.ResetPasswordResult;
import com.erp.module.system.controller.vo.UserVOs.UserDetail;
import com.erp.module.system.controller.vo.UserVOs.UserQuery;
import com.erp.module.system.controller.vo.UserVOs.UserResp;
import com.erp.module.system.controller.vo.UserVOs.UserSave;
import com.erp.module.system.controller.vo.UserVOs.UserSimple;
import com.erp.module.system.dal.dataobject.OrgDO;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.OrgMapper;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.dal.mapper.UserRoleMapper;
import com.erp.module.system.service.support.SystemCaches;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户管理（01-02），同时是 {@link UserApi} 的实现。 */
@Service
public class UserService implements UserApi {

    private static final String USERNAME_PATTERN = "[a-zA-Z][a-zA-Z0-9._-]{3,31}";
    private static final int MAX_PART_DEPTS = 10;
    private static final int MAX_SUPERIOR_DEPTH = 20;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final OrgMapper orgMapper;
    private final OrgService orgService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final ParamApi paramApi;
    private final SystemCaches caches;
    private final TransactionTemplate tx;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper, OrgMapper orgMapper,
                       OrgService orgService, PasswordEncoder passwordEncoder, PasswordPolicyService passwordPolicy,
                       ParamApi paramApi, SystemCaches caches, TransactionTemplate tx) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.orgMapper = orgMapper;
        this.orgService = orgService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.paramApi = paramApi;
        this.caches = caches;
        this.tx = tx;
    }

    // ==================== 查询 ====================

    public PageResult<UserResp> page(UserQuery q) {
        LambdaQueryWrapper<UserDO> w = buildQuery(q);
        Page<UserDO> page = Page.of(q.getPageNo(), q.getPageSize());
        boolean asc = "asc".equalsIgnoreCase(q.getSortOrder());
        String column = "lastLoginAt".equals(q.getSortField()) ? "last_login_at" : "created_at";
        page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
        page.addOrder(OrderItem.desc("id"));
        Page<UserDO> result = userMapper.selectPage(page, w);
        return new PageResult<>(toResps(result.getRecords()), result.getTotal());
    }

    /** 导出：与列表查询条件一致（不分页，最多 maxRows 行） */
    public List<UserResp> listForExport(UserQuery q, int maxRows) {
        q.setPageNo(1);
        q.setPageSize(Math.min(maxRows, 500));
        List<UserResp> all = new ArrayList<>();
        while (true) {
            PageResult<UserResp> p = page(q);
            all.addAll(p.list());
            if (all.size() >= p.total() || p.list().isEmpty() || all.size() >= maxRows) break;
            q.setPageNo(q.getPageNo() + 1);
        }
        return all.size() > maxRows ? all.subList(0, maxRows) : all;
    }

    private LambdaQueryWrapper<UserDO> buildQuery(UserQuery q) {
        LambdaQueryWrapper<UserDO> w = new LambdaQueryWrapper<>();
        if (q.getDeptId() != null) {
            Set<Long> ids = orgService.getSelfAndChildrenIds(q.getDeptId());
            w.in(UserDO::getDeptId, ids.isEmpty() ? Set.of(-1L) : ids);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(UserDO::getUsername, k).or().like(UserDO::getRealName, k)
                    .or().like(UserDO::getEmployeeNo, k).or().like(UserDO::getMobile, k));
        }
        if (StringUtils.hasText(q.getStatus())) w.eq(UserDO::getStatus, EnableStatus.valueOf(q.getStatus()));
        if (q.getRoleId() != null) {
            w.exists("SELECT 1 FROM sys_user_role ur WHERE ur.user_id = sys_user.id AND ur.role_id = {0}", q.getRoleId());
        }
        if (StringUtils.hasText(q.getPosition())) w.eq(UserDO::getPosition, q.getPosition());
        if (q.getLastLoginFrom() != null) w.ge(UserDO::getLastLoginAt, q.getLastLoginFrom().atStartOfDay());
        if (q.getLastLoginTo() != null) w.lt(UserDO::getLastLoginAt, q.getLastLoginTo().plusDays(1).atStartOfDay());
        return w;
    }

    private List<UserResp> toResps(List<UserDO> users) {
        if (users.isEmpty()) return List.of();
        Map<Long, List<String>> roleNames = roleNamesOf(users.stream().map(UserDO::getId).toList());
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        boolean fullMobile = me != null && me.hasPermission("system:user:update");
        LocalDateTime now = LocalDateTime.now();
        return users.stream().map(u -> new UserResp(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), u.getDeptId(),
                orgService.shortPathName(u.getDeptId()), roleNames.getOrDefault(u.getId(), List.of()), u.getPosition(),
                fullMobile ? u.getMobile() : maskMobile(u.getMobile()), u.getStatus().name(),
                u.getLockUntil() != null && u.getLockUntil().isAfter(now), u.getLastLoginAt(), u.getLastLoginIp(), u.getCreatedAt(),
                Boolean.TRUE.equals(u.getAdmin()))).toList();
    }

    private Map<Long, List<String>> roleNamesOf(Collection<Long> userIds) {
        Map<Long, List<String>> result = new HashMap<>();
        Map<Long, RoleDO> roles = roleMapper.selectList(null).stream().collect(Collectors.toMap(RoleDO::getId, r -> r));
        for (Long uid : userIds) {
            result.put(uid, userRoleMapper.selectRoleIds(uid).stream().map(roles::get).filter(Objects::nonNull).map(RoleDO::getName).toList());
        }
        return result;
    }

    static String maskMobile(String m) {
        if (m == null || m.length() < 7) return m;
        return m.substring(0, 3) + "****" + m.substring(m.length() - 4);
    }

    public UserDetail getDetail(Long id) {
        UserDO u = getUser(id);
        String superiorName = u.getSuperiorUserId() == null ? null
                : Optional.ofNullable(userMapper.selectById(u.getSuperiorUserId())).map(UserDO::getRealName).orElse(null);
        return new UserDetail(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), u.getGender(), u.getMobile(), u.getEmail(),
                u.getPosition(), u.getLanguage(), u.getRemark(), u.getDeptId(), userRoleMapper.selectPartDeptIds(id), u.getSuperiorUserId(),
                superiorName, userRoleMapper.selectRoleIds(id), u.getStatus().name(), Boolean.TRUE.equals(u.getAdmin()),
                u.getLockUntil() != null && u.getLockUntil().isAfter(LocalDateTime.now()), u.getLastLoginAt(), u.getVersion());
    }

    /** UserSelect 远程搜索：启用用户，最多 20 条；ids 用于回显（含停用） */
    public List<UserSimple> simple(String keyword, List<Long> ids) {
        LambdaQueryWrapper<UserDO> w = new LambdaQueryWrapper<>();
        if (ids != null && !ids.isEmpty()) {
            w.in(UserDO::getId, ids);
        } else {
            w.eq(UserDO::getStatus, EnableStatus.ENABLED);
            if (StringUtils.hasText(keyword)) {
                String k = keyword.trim();
                w.and(x -> x.like(UserDO::getRealName, k).or().like(UserDO::getUsername, k).or().like(UserDO::getEmployeeNo, k));
            }
            w.orderByAsc(UserDO::getRealName).last("LIMIT 20");
        }
        return userMapper.selectList(w).stream()
                .map(u -> new UserSimple(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), orgService.nameOf(u.getDeptId())))
                .toList();
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(UserSave req) {
        String username = req.username().trim().toLowerCase();
        if (!username.matches(USERNAME_PATTERN)) throw new BizException(SystemErrorCodes.USER_USERNAME_INVALID);
        if (userMapper.selectByUsername(username) != null) throw BizException.of(SystemErrorCodes.USER_USERNAME_DUPLICATE, username);
        UserDO u = new UserDO();
        u.setUsername(username);
        fill(u, req, null);
        String password = StringUtils.hasText(req.password()) ? req.password() : initPassword(username);
        passwordPolicy.check(password, username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(req.mustChangePassword() == null || req.mustChangePassword());
        u.setPasswordChangedAt(LocalDateTime.now());
        u.setTokenVersion(0);
        u.setFailCount(0);
        u.setAdmin(false);
        u.setStatus(EnableStatus.ENABLED);
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.USER_USERNAME_DUPLICATE, username);
        }
        saveRelations(u.getId(), req.roleIds(), req.partDeptIds(), null);
        return new CreateResult(u.getId(), password);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSave req) {
        UserDO u = getUser(id);
        List<Long> oldRoles = userRoleMapper.selectRoleIds(id);
        fill(u, req, id);
        u.setVersion(req.version());
        userMapper.updateByIdOrFail(u);
        saveRelations(id, req.roleIds(), req.partDeptIds(), oldRoles);
        caches.evictLoginUser(id);
    }

    /** 初始密码：参数 sys.user.init-password，为空时随机生成 */
    private String initPassword(String username) {
        String p = paramApi.getString("sys.user.init-password");
        return StringUtils.hasText(p) ? p : passwordPolicy.random(username);
    }

    private void fill(UserDO u, UserSave req, Long selfId) {
        u.setRealName(req.realName().trim());
        String employeeNo = trim(req.employeeNo());
        if (employeeNo != null) {
            UserDO other = userMapper.selectByEmployeeNo(employeeNo);
            if (other != null && !other.getId().equals(selfId)) {
                throw BizException.of(SystemErrorCodes.USER_EMPLOYEE_NO_DUPLICATE, employeeNo, other.getRealName());
            }
        }
        u.setEmployeeNo(employeeNo);
        String mobile = trim(req.mobile());
        if (mobile != null) {
            UserDO other = userMapper.selectByMobile(mobile);
            if (other != null && !other.getId().equals(selfId)) throw BizException.of(SystemErrorCodes.USER_MOBILE_DUPLICATE, other.getRealName());
        }
        u.setMobile(mobile);
        u.setEmail(trim(req.email()));
        u.setGender(StringUtils.hasText(req.gender()) ? req.gender() : "UNKNOWN");
        u.setPosition(trim(req.position()));
        u.setLanguage("en".equals(req.language()) ? "en" : "zh-CN");
        u.setRemark(trim(req.remark()));
        // 主部门（R07），所属公司由主部门推算
        OrgDO dept = orgMapper.selectById(req.deptId());
        if (dept == null) throw new BizException(SystemErrorCodes.ORG_NOT_EXISTS);
        if (dept.getStatus() != EnableStatus.ENABLED && !Objects.equals(u.getDeptId(), req.deptId())) {
            throw BizException.of(SystemErrorCodes.ORG_DISABLED, dept.getName());
        }
        u.setDeptId(dept.getId());
        u.setOrgId(orgService.getCompanyOf(dept.getId()).map(OrgDTO::id).orElse(dept.getId()));
        // 直属上级（R04）
        if (req.superiorUserId() != null) checkSuperior(selfId, req.superiorUserId());
        u.setSuperiorUserId(req.superiorUserId());
    }

    /** SYS-USR-R04：不能是自己，也不能是自己的下属（沿上级链向上查找不能回到自己，最多 20 层） */
    private void checkSuperior(Long selfId, Long superiorId) {
        if (superiorId.equals(selfId)) throw new BizException(SystemErrorCodes.USER_SUPERIOR_CYCLE);
        UserDO s = userMapper.selectById(superiorId);
        if (s == null) throw new BizException(SystemErrorCodes.USER_NOT_EXISTS);
        if (selfId == null) return;
        Long cursor = s.getSuperiorUserId();
        for (int i = 0; i < MAX_SUPERIOR_DEPTH && cursor != null; i++) {
            if (cursor.equals(selfId)) throw new BizException(SystemErrorCodes.USER_SUPERIOR_CYCLE);
            UserDO next = userMapper.selectById(cursor);
            cursor = next == null ? null : next.getSuperiorUserId();
        }
    }

    /** 角色（R05、R06）与兼职部门 */
    private void saveRelations(Long userId, List<Long> roleIds, List<Long> partDeptIds, List<Long> oldRoleIds) {
        Set<Long> roles = new LinkedHashSet<>(roleIds == null ? List.of() : roleIds);
        if (roles.isEmpty()) throw new BizException(SystemErrorCodes.USER_ROLE_REQUIRED);
        List<RoleDO> roleList = roleMapper.selectBatchIds(roles);
        if (roleList.size() != roles.size()) throw new BizException(SystemErrorCodes.ROLE_NOT_EXISTS);
        Set<Long> old = new HashSet<>(oldRoleIds == null ? List.of() : oldRoleIds);
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        boolean meSuper = me == null || me.isSuperAdmin();
        for (RoleDO r : roleList) {
            boolean added = !old.contains(r.getId());
            if (added && r.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.ROLE_DISABLED, r.getName());
            if (RoleDO.SUPER_ADMIN.equals(r.getCode()) && added && !meSuper) throw new BizException(SystemErrorCodes.USER_SUPER_ADMIN_ROLE_ONLY);
        }
        if (!meSuper) {
            RoleDO superRole = roleMapper.selectByCode(RoleDO.SUPER_ADMIN);
            if (superRole != null && old.contains(superRole.getId()) && !roles.contains(superRole.getId())) {
                throw new BizException(SystemErrorCodes.USER_SUPER_ADMIN_ROLE_ONLY);
            }
        }
        if (roleList.stream().noneMatch(r -> r.getStatus() == EnableStatus.ENABLED)) throw new BizException(SystemErrorCodes.USER_ROLE_REQUIRED);
        userRoleMapper.deleteByUser(userId);
        for (Long r : roles) userRoleMapper.insert(userId, r);

        UserDO u = userMapper.selectById(userId);
        Set<Long> parts = new LinkedHashSet<>(partDeptIds == null ? List.of() : partDeptIds);
        if (parts.contains(u.getDeptId()) || parts.size() > MAX_PART_DEPTS) throw new BizException(SystemErrorCodes.USER_PART_DEPT_INVALID);
        Set<Long> oldParts = new HashSet<>(userRoleMapper.selectPartDeptIds(userId));
        for (Long d : parts) {
            OrgDO dept = orgMapper.selectById(d);
            if (dept == null) throw new BizException(SystemErrorCodes.ORG_NOT_EXISTS);
            if (dept.getStatus() != EnableStatus.ENABLED && !oldParts.contains(d)) throw BizException.of(SystemErrorCodes.ORG_DISABLED, dept.getName());
        }
        userRoleMapper.deletePartDepts(userId);
        for (Long d : parts) userRoleMapper.insertPartDept(userId, d);
    }

    // ==================== 状态与安全 ====================

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        UserDO u = getUser(id);
        if (u.getStatus() == EnableStatus.ENABLED) return;
        u.setStatus(EnableStatus.ENABLED);
        u.setFailCount(0);
        u.setLockUntil(null);
        userMapper.updateByIdOrFail(u);
        userMapper.unlock(id);
        caches.evictLoginUser(id);
    }

    /** 停用（R08）：立即下线（令牌版本 +1） */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        UserDO u = getUser(id);
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        if (me != null && me.id().equals(id)) throw new BizException(SystemErrorCodes.USER_CANNOT_DISABLE_SELF);
        if (Boolean.TRUE.equals(u.getAdmin())) throw new BizException(SystemErrorCodes.USER_CANNOT_DISABLE_ADMIN);
        if (u.getStatus() == EnableStatus.DISABLED) return;
        u.setStatus(EnableStatus.DISABLED);
        userMapper.updateByIdOrFail(u);
        userMapper.increaseTokenVersion(id);
        caches.evictLoginUser(id);
    }

    /** 批量停用：逐条执行，部分失败时返回每条结果 */
    public List<BatchResult> batchDisable(List<Long> ids) {
        List<BatchResult> results = new ArrayList<>();
        for (Long id : ids) {
            UserDO u = userMapper.selectById(id);
            String name = u == null ? String.valueOf(id) : u.getRealName();
            try {
                tx.executeWithoutResult(s -> disable(id));
                results.add(new BatchResult(id, name, true, null));
            } catch (BizException e) {
                results.add(new BatchResult(id, name, false, e.getMessage()));
            }
        }
        return results;
    }

    public void unlock(Long id) {
        getUser(id);
        userMapper.unlock(id);
        caches.evictLoginUser(id);
    }

    /** 强制下线：令牌版本 +1，已签发的令牌全部失效 */
    public void kick(Long id) {
        getUser(id);
        userMapper.increaseTokenVersion(id);
        caches.evictLoginUser(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResult resetPassword(Long id, ResetPasswordReq req) {
        UserDO u = getUser(id);
        boolean random = !"MANUAL".equals(req.mode());
        String password = random ? passwordPolicy.random(u.getUsername()) : req.password();
        passwordPolicy.check(password, u.getUsername());
        passwordPolicy.remember(u.getId(), u.getPasswordHash());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(req.mustChangePassword() == null || req.mustChangePassword());
        u.setPasswordChangedAt(LocalDateTime.now());
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        u.setFailCount(0);
        u.setLockUntil(null);
        userMapper.updateByIdOrFail(u);
        caches.evictLoginUser(id);
        return new ResetPasswordResult(random ? password : null);
    }

    // ==================== 导入 ====================

    /** 校验导入行：逐行检查（含文件内用户名、工号、手机号重复；上级可引用同一文件中前面的行） */
    public void checkImport(List<ImportRow> rows, Map<String, String> positionByLabel) {
        if (!StringUtils.hasText(paramApi.getString("sys.user.init-password"))) {
            throw new BizException(SystemErrorCodes.USER_INIT_PASSWORD_REQUIRED);
        }
        Map<String, RoleDO> rolesByCode = roleMapper.selectList(null).stream().collect(Collectors.toMap(RoleDO::getCode, r -> r));
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmployeeNos = new HashSet<>();
        Set<String> seenMobiles = new HashSet<>();
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        for (ImportRow r : rows) {
            String username = r.get("username");
            if (username == null) r.error("用户名不能为空");
            else {
                username = username.toLowerCase();
                if (!username.matches(USERNAME_PATTERN)) r.error("用户名格式不正确");
                else if (!seenUsernames.add(username)) r.error("文件中用户名「" + username + "」重复");
                else if (userMapper.selectByUsername(username) != null) r.error("用户名「" + username + "」已存在");
            }
            if (r.get("realName") == null) r.error("姓名不能为空");
            else if (r.get("realName").length() > 32) r.error("姓名不能超过 32 个字");
            String no = r.get("employeeNo");
            if (no != null) {
                if (!seenEmployeeNos.add(no)) r.error("文件中工号「" + no + "」重复");
                else if (userMapper.selectByEmployeeNo(no) != null) r.error("工号「" + no + "」已被使用");
            }
            String deptCode = r.get("deptCode");
            if (deptCode == null) r.error("主部门编码不能为空");
            else {
                OrgDO dept = orgMapper.selectByCode(deptCode.toUpperCase());
                if (dept == null) r.error("部门编码 " + deptCode + " 不存在");
                else if (dept.getStatus() != EnableStatus.ENABLED) r.error("部门「" + dept.getName() + "」已停用");
            }
            String roleCodes = r.get("roleCodes");
            if (roleCodes == null) r.error("角色编码不能为空");
            else {
                for (String code : roleCodes.split("[,，]")) {
                    RoleDO role = rolesByCode.get(code.trim().toUpperCase());
                    if (role == null) r.error("角色编码 " + code.trim() + " 不存在");
                    else if (role.getStatus() != EnableStatus.ENABLED) r.error("角色「" + role.getName() + "」已停用");
                    else if (RoleDO.SUPER_ADMIN.equals(role.getCode()) && me != null && !me.isSuperAdmin()) r.error("只有超级管理员可以分配超级管理员角色");
                }
            }
            String mobile = r.get("mobile");
            if (mobile != null) {
                if (!mobile.matches("^(1\\d{10}|\\+\\d{6,20})$")) r.error("手机号格式不正确");
                else if (!seenMobiles.add(mobile)) r.error("文件中手机号重复");
                else if (userMapper.selectByMobile(mobile) != null) r.error("手机号已被使用");
            }
            String email = r.get("email");
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) r.error("邮箱格式不正确");
            String position = r.get("position");
            if (position != null && !positionByLabel.containsKey(position)) r.error("岗位「" + position + "」不存在");
            String superior = r.get("superiorUsername");
            if (superior != null && !seenUsernames.contains(superior.toLowerCase()) && userMapper.selectByUsername(superior) == null) {
                r.error("直属上级用户名「" + superior + "」不存在");
            }
        }
    }

    /** 执行导入（不允许部分导入：调用前所有行必须校验通过），同一事务 */
    @Transactional(rollbackFor = Exception.class)
    public int importUsers(List<ImportRow> rows, Map<String, String> positionByLabel) {
        String password = paramApi.getString("sys.user.init-password");
        Map<String, RoleDO> rolesByCode = roleMapper.selectList(null).stream().collect(Collectors.toMap(RoleDO::getCode, r -> r));
        Map<String, Long> created = new HashMap<>();
        List<ImportRow> withSuperior = new ArrayList<>();
        for (ImportRow r : rows) {
            OrgDO dept = orgMapper.selectByCode(r.get("deptCode").toUpperCase());
            List<Long> roleIds = new ArrayList<>();
            for (String code : r.get("roleCodes").split("[,，]")) roleIds.add(rolesByCode.get(code.trim().toUpperCase()).getId());
            UserSave save = new UserSave(r.get("username"), r.get("realName"), r.get("employeeNo"), null, r.get("mobile"), r.get("email"),
                    r.get("position") == null ? null : positionByLabel.get(r.get("position")), null, null, dept.getId(), List.of(), null,
                    roleIds, password, true, null);
            CreateResult res = create(save);
            created.put(r.get("username").toLowerCase(), res.id());
            if (r.get("superiorUsername") != null) withSuperior.add(r);
        }
        for (ImportRow r : withSuperior) {
            String sup = r.get("superiorUsername").toLowerCase();
            Long supId = created.containsKey(sup) ? created.get(sup) : userMapper.selectByUsername(sup).getId();
            UserDO u = userMapper.selectById(created.get(r.get("username").toLowerCase()));
            u.setSuperiorUserId(supId);
            userMapper.updateByIdOrFail(u);
        }
        return rows.size();
    }

    // ==================== UserApi ====================

    private UserDO getUser(Long id) {
        UserDO u = userMapper.selectById(id);
        if (u == null) throw new BizException(SystemErrorCodes.USER_NOT_EXISTS);
        return u;
    }

    private UserDTO toDTO(UserDO u) {
        return new UserDTO(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), u.getOrgId(), u.getDeptId(),
                orgService.nameOf(u.getDeptId()), u.getMobile(), u.getEmail(), u.getStatus() == EnableStatus.ENABLED);
    }

    @Override
    public Optional<UserDTO> get(Long id) {
        return Optional.ofNullable(id == null ? null : userMapper.selectById(id)).map(this::toDTO);
    }

    @Override
    public Map<Long, UserDTO> list(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(UserDO::getId, this::toDTO));
    }

    @Override
    public Optional<UserDTO> getDeptLeader(Long deptId) {
        return orgService.get(deptId).map(OrgDTO::leaderUserId).flatMap(this::get);
    }

    @Override
    public Optional<UserDTO> getSuperior(Long userId) {
        return get(userId).flatMap(u -> Optional.ofNullable(userMapper.selectById(userId).getSuperiorUserId())).flatMap(this::get);
    }

    @Override
    public List<UserDTO> listByRole(Long roleId) {
        return userMapper.selectByRole(roleId).stream().filter(u -> u.getStatus() == EnableStatus.ENABLED).map(this::toDTO).toList();
    }

    @Override
    public List<UserDTO> listByPermission(String permission) {
        return userMapper.selectByPermission(permission).stream().map(this::toDTO).toList();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
