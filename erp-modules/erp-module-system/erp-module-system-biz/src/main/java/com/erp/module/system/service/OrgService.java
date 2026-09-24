package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.org.OrgReferenceChecker;
import com.erp.module.system.controller.vo.OrgVOs.OrgNode;
import com.erp.module.system.controller.vo.OrgVOs.OrgResp;
import com.erp.module.system.controller.vo.OrgVOs.OrgSave;
import com.erp.module.system.controller.vo.OrgVOs.SimpleNode;
import com.erp.module.system.dal.dataobject.OrgDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.OrgMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.enums.OrgType;
import com.erp.module.system.service.support.SystemCaches;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 组织架构（01-01），同时是 {@link OrgApi} 的实现（全部组织缓存在内存中，组织数量有限）。 */
@Service
public class OrgService implements OrgApi {

    static final int MAX_LEVEL = 8;

    private final OrgMapper orgMapper;
    private final UserMapper userMapper;
    private final SystemCaches caches;
    private final List<OrgReferenceChecker> referenceCheckers;

    public OrgService(OrgMapper orgMapper, UserMapper userMapper, SystemCaches caches, List<OrgReferenceChecker> referenceCheckers) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.caches = caches;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 查询 ====================

    /** 完整树（含停用）；关键字命中节点及其所有祖先都显示（SYS-ORG-T08） */
    public List<OrgNode> tree(String keyword, String status) {
        List<OrgDO> all = orgMapper.selectList(null);
        Map<Long, OrgDO> byId = all.stream().collect(Collectors.toMap(OrgDO::getId, o -> o));
        Set<Long> visible = new HashSet<>();
        String k = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        for (OrgDO o : all) {
            boolean statusOk = !StringUtils.hasText(status) || o.getStatus().name().equals(status);
            boolean keywordOk = k == null || o.getName().toLowerCase().contains(k) || o.getCode().toLowerCase().contains(k);
            if (statusOk && keywordOk) {
                for (Long id : pathIds(o.getPath())) if (byId.containsKey(id)) visible.add(id);
            }
        }
        Map<Long, Long> userCounts = userMapper.countEnabledGroupByDept().stream()
                .filter(c -> c.id() != null).collect(Collectors.toMap(UserMapper.IdCount::id, UserMapper.IdCount::cnt));
        Set<Long> leaderIds = all.stream().map(OrgDO::getLeaderUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(UserDO::getId, UserDO::getRealName));
        return buildTree(all.stream().filter(o -> visible.contains(o.getId())).toList(), null,
                o -> new OrgNode(o.getId(), o.getParentId(), o.getCode(), o.getName(), o.getShortName(), o.getOrgType().name(),
                        o.getLeaderUserId(), leaderNames.get(o.getLeaderUserId()), o.getPhone(),
                        userCounts.getOrDefault(o.getId(), 0L).intValue(), o.getSort(), o.getStatus().name(), o.getLevel(), new ArrayList<>()),
                OrgNode::children);
    }

    /** 仅启用节点的精简树（OrgTreeSelect） */
    public List<SimpleNode> simpleTree() {
        List<OrgDO> enabled = all().values().stream().filter(o -> o.getStatus() == EnableStatus.ENABLED).toList();
        return buildTree(enabled, null,
                o -> new SimpleNode(o.getId(), o.getParentId(), o.getCode(), o.getName(), o.getOrgType().name(), new ArrayList<>()),
                SimpleNode::children);
    }

    private static <N> List<N> buildTree(List<OrgDO> orgs, Long rootParent, java.util.function.Function<OrgDO, N> mapper,
                                         java.util.function.Function<N, List<N>> childrenOf) {
        Set<Long> ids = orgs.stream().map(OrgDO::getId).collect(Collectors.toSet());
        Map<Long, N> nodes = new LinkedHashMap<>();
        List<OrgDO> sorted = orgs.stream().sorted(Comparator.comparingInt(OrgDO::getLevel).thenComparingInt(OrgDO::getSort).thenComparing(OrgDO::getId)).toList();
        List<N> roots = new ArrayList<>();
        for (OrgDO o : sorted) {
            N n = mapper.apply(o);
            nodes.put(o.getId(), n);
            if (o.getParentId() == null || Objects.equals(o.getParentId(), rootParent) || !ids.contains(o.getParentId())) roots.add(n);
            else childrenOf.apply(nodes.get(o.getParentId())).add(n);
        }
        return roots;
    }

    public OrgResp getDetail(Long id) {
        OrgDO o = getOrg(id);
        String leaderName = o.getLeaderUserId() == null ? null : Optional.ofNullable(userMapper.selectById(o.getLeaderUserId())).map(UserDO::getRealName).orElse(null);
        return new OrgResp(o.getId(), o.getParentId(), o.getCode(), o.getName(), o.getShortName(), o.getOrgType().name(),
                o.getLeaderUserId(), leaderName, o.getPhone(), o.getAddress(), o.getNameEn(), o.getAddressEn(), o.getTaxNo(),
                o.getLogoFileId(), o.getSort(), o.getStatus().name(), o.getRemark(), o.getVersion());
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(OrgSave req) {
        OrgType type = OrgType.valueOf(req.orgType());
        OrgDO parent = req.parentId() == null ? null : getOrg(req.parentId());
        checkHierarchy(type, parent);
        if (parent != null && parent.getLevel() + 1 > MAX_LEVEL) throw new BizException(SystemErrorCodes.ORG_TOO_DEEP);
        String code = req.code().trim().toUpperCase();
        checkCodeUnique(code, null);
        checkNameUnique(req.parentId(), req.name().trim(), null);
        checkLeader(req.leaderUserId());
        OrgDO o = new OrgDO();
        o.setId(IdWorker.getId());
        o.setParentId(req.parentId());
        o.setOrgType(type);
        o.setCode(code);
        fill(o, req);
        o.setPath((parent == null ? "/" : parent.getPath()) + o.getId() + "/");
        o.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        o.setStatus(EnableStatus.ENABLED);
        try {
            orgMapper.insert(o);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.ORG_CODE_DUPLICATE, code);
        }
        changed();
        return o.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OrgSave req) {
        OrgDO o = getOrg(id);
        String code = req.code().trim().toUpperCase();
        checkCodeUnique(code, id);
        checkNameUnique(req.parentId(), req.name().trim(), id);
        checkLeader(req.leaderUserId());
        if (!Objects.equals(o.getParentId(), req.parentId())) {
            move(o, req.parentId());
        }
        o.setCode(code);
        fill(o, req);
        o.setVersion(req.version());
        try {
            orgMapper.updateByIdOrFail(o);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.ORG_CODE_DUPLICATE, code);
        }
        changed();
    }

    /** 修改上级（SYS-ORG-R01~R03、R09）：同步更新自身及全部下级的 path、level */
    private void move(OrgDO o, Long newParentId) {
        OrgDO parent = newParentId == null ? null : getOrg(newParentId);
        if (parent != null && parent.getPath().startsWith(o.getPath())) throw new BizException(SystemErrorCodes.ORG_PARENT_CYCLE);
        checkHierarchy(o.getOrgType(), parent);
        int newLevel = parent == null ? 1 : parent.getLevel() + 1;
        int subtreeDepth = orgMapper.selectDescendants(o.getPath()).stream().mapToInt(OrgDO::getLevel).max().orElse(o.getLevel()) - o.getLevel();
        if (newLevel + subtreeDepth > MAX_LEVEL) throw new BizException(SystemErrorCodes.ORG_TOO_DEEP);
        if (o.getStatus() == EnableStatus.ENABLED && o.getOrgType() == OrgType.COMPANY && o.getParentId() == null) {
            checkNotLastRootCompany(o.getId());
        }
        String oldPath = o.getPath();
        String newPath = (parent == null ? "/" : parent.getPath()) + o.getId() + "/";
        orgMapper.updateDescendantPaths(oldPath, oldPath.length(), newPath, newLevel - o.getLevel());
        o.setParentId(newParentId);
        o.setPath(newPath);
        o.setLevel(newLevel);
    }

    /** SYS-ORG-R01：根节点必须是公司；部门下不能建公司 */
    private static void checkHierarchy(OrgType type, OrgDO parent) {
        if (parent == null && type != OrgType.COMPANY) throw new BizException(SystemErrorCodes.ORG_ROOT_MUST_BE_COMPANY);
        if (parent != null && parent.getOrgType() == OrgType.DEPT && type == OrgType.COMPANY) {
            throw new BizException(SystemErrorCodes.ORG_DEPT_CANNOT_HAVE_COMPANY);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        OrgDO o = getOrg(id);
        if (o.getStatus() == EnableStatus.ENABLED) return;
        if (o.getParentId() != null && getOrg(o.getParentId()).getStatus() != EnableStatus.ENABLED) {
            throw new BizException(SystemErrorCodes.ORG_PARENT_DISABLED);
        }
        o.setStatus(EnableStatus.ENABLED);
        orgMapper.updateByIdOrFail(o);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        OrgDO o = getOrg(id);
        if (o.getStatus() == EnableStatus.DISABLED) return;
        if (orgMapper.countEnabledChildren(id) > 0) throw new BizException(SystemErrorCodes.ORG_HAS_ENABLED_CHILDREN);
        long users = userMapper.countEnabledByDept(id);
        if (users > 0) throw BizException.of(SystemErrorCodes.ORG_HAS_ENABLED_USERS, users);
        if (o.getParentId() == null) checkNotLastRootCompany(id);
        o.setStatus(EnableStatus.DISABLED);
        orgMapper.updateByIdOrFail(o);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgDO o = getOrg(id);
        if (!orgMapper.selectChildren(id).isEmpty()) throw new BizException(SystemErrorCodes.ORG_HAS_CHILDREN);
        if (userMapper.countAllByOrg(id) > 0) throw new BizException(SystemErrorCodes.ORG_HAS_USERS);
        for (OrgReferenceChecker c : referenceCheckers) {
            if (c.isReferenced(id)) throw new BizException(SystemErrorCodes.ORG_REFERENCED);
        }
        if (o.getParentId() == null && o.getStatus() == EnableStatus.ENABLED) checkNotLastRootCompany(id);
        orgMapper.deleteById(id);
        changed();
    }

    /** SYS-ORG-R10：至少保留一个启用的根公司 */
    private void checkNotLastRootCompany(Long excludeId) {
        boolean another = all().values().stream().anyMatch(x -> x.getParentId() == null && x.getOrgType() == OrgType.COMPANY
                && x.getStatus() == EnableStatus.ENABLED && !x.getId().equals(excludeId));
        if (!another) throw new BizException(SystemErrorCodes.ORG_LAST_COMPANY);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        OrgDO exists = orgMapper.selectByCode(code);
        if (exists != null && !exists.getId().equals(excludeId)) throw BizException.of(SystemErrorCodes.ORG_CODE_DUPLICATE, code);
    }

    private void checkNameUnique(Long parentId, String name, Long excludeId) {
        List<OrgDO> siblings = parentId == null
                ? orgMapper.selectList(null).stream().filter(x -> x.getParentId() == null).toList()
                : orgMapper.selectChildren(parentId);
        if (siblings.stream().anyMatch(x -> x.getName().equals(name) && !x.getId().equals(excludeId))) {
            throw BizException.of(SystemErrorCodes.ORG_NAME_DUPLICATE, name);
        }
    }

    private void checkLeader(Long leaderUserId) {
        if (leaderUserId == null) return;
        UserDO u = userMapper.selectById(leaderUserId);
        if (u == null || u.getStatus() != EnableStatus.ENABLED) throw new BizException(SystemErrorCodes.USER_NOT_EXISTS);
    }

    private static void fill(OrgDO o, OrgSave req) {
        o.setName(req.name().trim());
        o.setShortName(trim(req.shortName()));
        o.setLeaderUserId(req.leaderUserId());
        o.setPhone(trim(req.phone()));
        o.setSort(req.sort());
        o.setRemark(trim(req.remark()));
        if (o.getOrgType() == OrgType.COMPANY) {
            o.setNameEn(trim(req.nameEn()));
            o.setAddress(trim(req.address()));
            o.setAddressEn(trim(req.addressEn()));
            o.setTaxNo(trim(req.taxNo()));
            o.setLogoFileId(req.logoFileId());
        } else {
            o.setAddress(trim(req.address()));
        }
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private OrgDO getOrg(Long id) {
        OrgDO o = orgMapper.selectById(id);
        if (o == null) throw new BizException(SystemErrorCodes.ORG_NOT_EXISTS);
        return o;
    }

    /** 组织变化后清除组织缓存和全部用户的数据范围缓存 */
    private void changed() {
        caches.clear(SystemCaches.ORG);
        caches.clearLoginUsers();
    }

    static List<Long> pathIds(String path) {
        List<Long> ids = new ArrayList<>();
        for (String s : path.split("/")) if (!s.isEmpty()) ids.add(Long.valueOf(s));
        return ids;
    }

    // ==================== OrgApi（缓存） ====================

    private Map<Long, OrgDO> all() {
        return caches.get(SystemCaches.ORG, "all", () -> {
            Map<Long, OrgDO> m = new LinkedHashMap<>();
            orgMapper.selectList(null).forEach(o -> m.put(o.getId(), o));
            return m;
        });
    }

    private static OrgDTO toDTO(OrgDO o) {
        return new OrgDTO(o.getId(), o.getParentId(), o.getCode(), o.getName(), o.getShortName(), o.getOrgType().name(),
                o.getLeaderUserId(), o.getNameEn(), o.getAddress(), o.getAddressEn(), o.getTaxNo(), o.getPhone(),
                o.getStatus() == EnableStatus.ENABLED);
    }

    @Override
    public Optional<OrgDTO> get(Long id) {
        return Optional.ofNullable(id == null ? null : all().get(id)).map(OrgService::toDTO);
    }

    @Override
    public Map<Long, OrgDTO> list(Collection<Long> ids) {
        Map<Long, OrgDTO> m = new HashMap<>();
        Map<Long, OrgDO> all = all();
        for (Long id : ids) {
            OrgDO o = all.get(id);
            if (o != null) m.put(id, toDTO(o));
        }
        return m;
    }

    @Override
    public Set<Long> getSelfAndChildrenIds(Long id) {
        OrgDO self = all().get(id);
        if (self == null) return Set.of();
        return all().values().stream().filter(o -> o.getPath().startsWith(self.getPath())).map(OrgDO::getId).collect(Collectors.toSet());
    }

    @Override
    public Optional<OrgDTO> getCompanyOf(Long deptId) {
        OrgDO o = deptId == null ? null : all().get(deptId);
        if (o == null) return Optional.empty();
        List<Long> ids = pathIds(o.getPath());
        for (int i = ids.size() - 1; i >= 0; i--) {
            OrgDO x = all().get(ids.get(i));
            if (x != null && x.getOrgType() == OrgType.COMPANY) return Optional.of(toDTO(x));
        }
        return Optional.empty();
    }

    /** 部门显示名：全路径的最后两级，如“生产部/SMT 车间” */
    public String shortPathName(Long deptId) {
        OrgDO o = deptId == null ? null : all().get(deptId);
        if (o == null) return null;
        List<Long> ids = pathIds(o.getPath());
        if (ids.size() >= 2) {
            OrgDO parent = all().get(ids.get(ids.size() - 2));
            if (parent != null && parent.getOrgType() == OrgType.DEPT) return parent.getName() + "/" + o.getName();
        }
        return o.getName();
    }

    public boolean isEnabled(Long id) {
        OrgDO o = id == null ? null : all().get(id);
        return o != null && o.getStatus() == EnableStatus.ENABLED;
    }

    public String nameOf(Long id) {
        OrgDO o = id == null ? null : all().get(id);
        return o == null ? null : o.getName();
    }
}
