package com.erp.module.system.service.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.module.ErpModule;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import com.erp.module.system.api.workflow.ApprovalBizDefinition.FieldType;
import com.erp.module.system.controller.vo.WorkflowVOs.BizTypeResp;
import com.erp.module.system.controller.vo.WorkflowVOs.BranchSave;
import com.erp.module.system.controller.vo.WorkflowVOs.BranchView;
import com.erp.module.system.controller.vo.WorkflowVOs.Condition;
import com.erp.module.system.controller.vo.WorkflowVOs.DefinitionSave;
import com.erp.module.system.controller.vo.WorkflowVOs.DefinitionView;
import com.erp.module.system.controller.vo.WorkflowVOs.Definitions;
import com.erp.module.system.controller.vo.WorkflowVOs.HistoryResp;
import com.erp.module.system.controller.vo.WorkflowVOs.NodeSave;
import com.erp.module.system.controller.vo.WorkflowVOs.NodeView;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.dataobject.WfBizTypeDO;
import com.erp.module.system.dal.dataobject.WfBranchDO;
import com.erp.module.system.dal.dataobject.WfDefinitionDO;
import com.erp.module.system.dal.dataobject.WfNodeDO;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.erp.module.system.dal.mapper.WfBizTypeMapper;
import com.erp.module.system.dal.mapper.WfBranchMapper;
import com.erp.module.system.dal.mapper.WfDefinitionMapper;
import com.erp.module.system.dal.mapper.WfNodeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

/**
 * 审批流程定义（需求 08 第 2、4.1 节）：单据类型同步、草稿（基于当前版本复制）、保存（整体覆盖分支与节点）、
 * 发布（R05 校验，原版本归档）、启用开关、历史版本。进行中的实例始终按发起时的版本执行（R09）。
 */
@Slf4j
@Service
public class WfDefinitionService {

    private final WfBizTypeMapper bizTypeMapper;
    private final WfDefinitionMapper definitionMapper;
    private final WfBranchMapper branchMapper;
    private final WfNodeMapper nodeMapper;
    private final RoleMapper roleMapper;
    private final UserApi userApi;
    private final ObjectMapper objectMapper;
    private final Map<String, String> moduleNames;

    public WfDefinitionService(WfBizTypeMapper bizTypeMapper, WfDefinitionMapper definitionMapper, WfBranchMapper branchMapper,
                               WfNodeMapper nodeMapper, RoleMapper roleMapper, UserApi userApi, ObjectMapper objectMapper,
                               List<ErpModule> modules) {
        this.bizTypeMapper = bizTypeMapper;
        this.definitionMapper = definitionMapper;
        this.branchMapper = branchMapper;
        this.nodeMapper = nodeMapper;
        this.roleMapper = roleMapper;
        this.userApi = userApi;
        this.objectMapper = objectMapper;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
    }

    // ==================== 声明同步 ====================

    @Transactional
    public void syncBizTypes(List<ApprovalBizDefinition> defs) {
        Map<String, ApprovalBizDefinition> byType = new LinkedHashMap<>();
        for (ApprovalBizDefinition d : defs) {
            ApprovalBizDefinition old = byType.put(d.bizType(), d);
            if (old != null) {
                throw new IllegalStateException("审批单据类型重复声明: " + d.bizType() + "（" + old.moduleCode() + "、" + d.moduleCode() + "）");
            }
        }
        Map<String, WfBizTypeDO> existing = bizTypeMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(WfBizTypeDO::getBizType, t -> t));
        for (ApprovalBizDefinition d : byType.values()) {
            WfBizTypeDO t = existing.getOrDefault(d.bizType(), new WfBizTypeDO());
            t.setBizType(d.bizType());
            t.setName(d.name());
            t.setModuleCode(d.moduleCode());
            t.setDetailRoute(d.detailRoute());
            t.setFields(json(d.fields()));
            t.setUserFields(json(d.userFields()));
            t.setActive(true);
            if (t.getId() == null) bizTypeMapper.insert(t);
            else bizTypeMapper.updateByIdOrFail(t);
        }
        for (WfBizTypeDO t : existing.values()) {
            if (!byType.containsKey(t.getBizType()) && Boolean.TRUE.equals(t.getActive())) {
                t.setActive(false);
                bizTypeMapper.updateByIdOrFail(t);
            }
        }
    }

    // ==================== 查询 ====================

    public List<BizTypeResp> bizTypes() {
        List<WfBizTypeDO> types = bizTypeMapper.selectList(new LambdaQueryWrapper<WfBizTypeDO>().eq(WfBizTypeDO::getActive, true)
                .orderByAsc(WfBizTypeDO::getModuleCode).orderByAsc(WfBizTypeDO::getBizType));
        Map<String, List<WfDefinitionDO>> defs = definitionMapper.selectList(new LambdaQueryWrapper<WfDefinitionDO>()
                        .in(WfDefinitionDO::getStatus, WfDefinitionDO.ACTIVE, WfDefinitionDO.DRAFT))
                .stream().collect(Collectors.groupingBy(WfDefinitionDO::getBizType));
        return types.stream().map(t -> {
            List<WfDefinitionDO> ds = defs.getOrDefault(t.getBizType(), List.of());
            WfDefinitionDO active = ds.stream().filter(d -> WfDefinitionDO.ACTIVE.equals(d.getStatus())).findFirst().orElse(null);
            boolean hasDraft = ds.stream().anyMatch(d -> WfDefinitionDO.DRAFT.equals(d.getStatus()));
            String status = active == null ? "NONE" : Boolean.TRUE.equals(active.getEnabled()) ? "ENABLED" : "DISABLED";
            return new BizTypeResp(t.getBizType(), t.getName(), t.getModuleCode(), moduleNames.getOrDefault(t.getModuleCode(), t.getModuleCode()),
                    status, hasDraft, active == null ? null : active.getDefVersion(), tree(t.getFields()), tree(t.getUserFields()));
        }).toList();
    }

    public Definitions definitions(String bizType) {
        bizType(bizType);
        return new Definitions(find(bizType, WfDefinitionDO.ACTIVE).map(this::view).orElse(null),
                find(bizType, WfDefinitionDO.DRAFT).map(this::view).orElse(null));
    }

    public DefinitionView get(Long id) {
        return view(getDef(id));
    }

    public List<HistoryResp> history(String bizType) {
        List<WfDefinitionDO> list = definitionMapper.selectList(new LambdaQueryWrapper<WfDefinitionDO>().eq(WfDefinitionDO::getBizType, bizType)
                .in(WfDefinitionDO::getStatus, WfDefinitionDO.ACTIVE, WfDefinitionDO.ARCHIVED).orderByDesc(WfDefinitionDO::getDefVersion));
        Map<Long, UserDTO> users = userApi.list(list.stream().map(WfDefinitionDO::getPublishedBy).filter(Objects::nonNull).distinct().toList());
        return list.stream().map(d -> new HistoryResp(d.getId(), d.getDefVersion(), d.getStatus(), d.getPublishedAt(),
                nameOf(users, d.getPublishedBy()), d.getRemark())).toList();
    }

    // ==================== 草稿与发布 ====================

    /** 有草稿则返回草稿；否则以 ACTIVE 版本复制；都没有时新建只有“其他情况”分支的空草稿 */
    @Transactional
    public DefinitionView draft(String bizType) {
        bizType(bizType);
        Optional<WfDefinitionDO> draft = find(bizType, WfDefinitionDO.DRAFT);
        if (draft.isPresent()) return view(draft.get());
        Optional<WfDefinitionDO> active = find(bizType, WfDefinitionDO.ACTIVE);
        WfDefinitionDO d = new WfDefinitionDO();
        d.setBizType(bizType);
        d.setDefVersion(nextVersion(bizType));
        d.setStatus(WfDefinitionDO.DRAFT);
        d.setEnabled(true);
        d.setSkipInitiator(active.map(WfDefinitionDO::getSkipInitiator).orElse(true));
        d.setSkipDuplicate(active.map(WfDefinitionDO::getSkipDuplicate).orElse(true));
        d.setEmptyPolicy(active.map(WfDefinitionDO::getEmptyPolicy).orElse(WfDefinitionDO.EMPTY_TO_ADMIN));
        d.setBasedOn(active.map(WfDefinitionDO::getDefVersion).orElse(null));
        definitionMapper.insert(d);
        if (active.isPresent()) {
            for (Model.Branch b : load(active.get()).branches()) {
                WfBranchDO nb = insertBranch(d.getId(), b.priority(), b.name(), b.isDefault(), b.conditionsJson());
                for (WfNodeDO n : b.nodes()) insertNode(nb.getId(), n.getSeq(), n.getName(), n.getApproverType(), n.getApproverValue(), n.getMultiMode());
            }
        } else {
            insertBranch(d.getId(), WfBranchDO.DEFAULT_PRIORITY, "其他情况", true, "[]");
        }
        return view(d);
    }

    /** 保存草稿：整体覆盖分支和节点；“其他情况”分支固定在最后 */
    @Transactional
    public DefinitionView saveDraft(Long id, DefinitionSave req) {
        WfDefinitionDO d = getDef(id);
        if (!WfDefinitionDO.DRAFT.equals(d.getStatus())) throw BizException.of(SystemErrorCodes.WF_NOT_DRAFT);
        long defaults = req.branches().stream().filter(BranchSave::isDefault).count();
        if (defaults != 1) throw BizException.of(SystemErrorCodes.WF_NO_DEFAULT_BRANCH);
        d.setSkipInitiator(req.skipInitiator());
        d.setSkipDuplicate(req.skipDuplicate());
        d.setEmptyPolicy(WfDefinitionDO.EMPTY_AUTO_PASS.equals(req.emptyPolicy()) ? WfDefinitionDO.EMPTY_AUTO_PASS : WfDefinitionDO.EMPTY_TO_ADMIN);
        definitionMapper.updateByIdOrFail(d);
        List<WfBranchDO> oldBranches = branchMapper.selectList(new LambdaQueryWrapper<WfBranchDO>().eq(WfBranchDO::getDefinitionId, id));
        if (!oldBranches.isEmpty()) {
            nodeMapper.delete(new LambdaQueryWrapper<WfNodeDO>().in(WfNodeDO::getBranchId, oldBranches.stream().map(WfBranchDO::getId).toList()));
            branchMapper.delete(new LambdaQueryWrapper<WfBranchDO>().eq(WfBranchDO::getDefinitionId, id));
        }
        List<BranchSave> ordered = new ArrayList<>(req.branches().stream().filter(b -> !b.isDefault()).toList());
        ordered.addAll(req.branches().stream().filter(BranchSave::isDefault).toList());
        int priority = 10;
        for (BranchSave b : ordered) {
            WfBranchDO nb = insertBranch(id, b.isDefault() ? WfBranchDO.DEFAULT_PRIORITY : priority, b.name().trim(), b.isDefault(),
                    b.isDefault() ? "[]" : json(b.conditions() == null ? List.of() : b.conditions()));
            priority += 10;
            int seq = 1;
            for (NodeSave n : b.nodes() == null ? List.<NodeSave>of() : b.nodes()) {
                String type = WfApprovers.TYPES.contains(n.approverType()) ? n.approverType() : WfApprovers.DEPT_LEADER;
                String mode = WfApprovers.SINGLE.contains(type) || !"ALL".equals(n.multiMode()) ? "ANY" : "ALL";
                insertNode(nb.getId(), seq++, n.name().trim(), type, n.approverValue() == null || n.approverValue().isNull() ? null : n.approverValue().toString(), mode);
            }
        }
        return view(getDef(id));
    }

    @Transactional
    public void discardDraft(Long id) {
        WfDefinitionDO d = getDef(id);
        if (!WfDefinitionDO.DRAFT.equals(d.getStatus())) throw BizException.of(SystemErrorCodes.WF_NOT_DRAFT);
        List<Long> branchIds = branchMapper.selectList(new LambdaQueryWrapper<WfBranchDO>().eq(WfBranchDO::getDefinitionId, id))
                .stream().map(WfBranchDO::getId).toList();
        if (!branchIds.isEmpty()) nodeMapper.delete(new LambdaQueryWrapper<WfNodeDO>().in(WfNodeDO::getBranchId, branchIds));
        branchMapper.delete(new LambdaQueryWrapper<WfBranchDO>().eq(WfBranchDO::getDefinitionId, id));
        definitionMapper.deleteById(id);
    }

    /** 发布（R05 校验）：原 ACTIVE 版本归档，启用状态沿用原版本 */
    @Transactional
    public void publish(Long id, String remark) {
        WfDefinitionDO d = getDef(id);
        if (!WfDefinitionDO.DRAFT.equals(d.getStatus())) throw BizException.of(SystemErrorCodes.WF_NOT_DRAFT);
        validate(d);
        Optional<WfDefinitionDO> active = find(d.getBizType(), WfDefinitionDO.ACTIVE);
        active.ifPresent(a -> {
            a.setStatus(WfDefinitionDO.ARCHIVED);
            definitionMapper.updateByIdOrFail(a);
        });
        d.setStatus(WfDefinitionDO.ACTIVE);
        d.setEnabled(active.map(WfDefinitionDO::getEnabled).orElse(true));
        d.setPublishedAt(LocalDateTime.now());
        d.setPublishedBy(SecurityUtils.getLoginUserIdOrNull());
        d.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        definitionMapper.updateByIdOrFail(d);
    }

    /** 启用审批开关：立即生效，不影响进行中的实例 */
    @Transactional
    public void setEnabled(String bizType, boolean enabled) {
        WfDefinitionDO a = find(bizType, WfDefinitionDO.ACTIVE).orElseThrow(() -> BizException.of(SystemErrorCodes.WF_NO_ACTIVE_VERSION));
        a.setEnabled(enabled);
        definitionMapper.updateByIdOrFail(a);
    }

    /** R05 发布校验 */
    void validate(WfDefinitionDO d) {
        WfBizTypeDO type = bizType(d.getBizType());
        Map<String, FieldType> fields = fieldTypes(type);
        Set<String> userFields = new HashSet<>();
        tree(type.getUserFields()).forEach(f -> userFields.add(f.path("code").asText()));
        Model m = load(d);
        if (m.branches().stream().noneMatch(Model.Branch::isDefault)) throw BizException.of(SystemErrorCodes.WF_NO_DEFAULT_BRANCH);
        for (Model.Branch b : m.branches()) {
            if (!b.isDefault()) {
                if (b.conditions().isEmpty()) throw BizException.of(SystemErrorCodes.WF_BRANCH_NO_CONDITION, b.name());
                for (WfConditions.Cond c : b.conditions()) {
                    FieldType ft = fields.get(c.field());
                    if (ft == null) throw BizException.of(SystemErrorCodes.WF_CONDITION_FIELD, b.name(), c.field());
                    if (!WfConditions.OPS.get(ft).contains(c.op()) || !WfConditions.complete(ft, c.op(), c.value())) {
                        throw BizException.of(SystemErrorCodes.WF_CONDITION_INCOMPLETE, b.name());
                    }
                }
            }
            if (b.nodes().isEmpty()) throw BizException.of(SystemErrorCodes.WF_BRANCH_NO_NODE, b.name());
            for (WfNodeDO n : b.nodes()) validateNode(n, userFields);
        }
    }

    private void validateNode(WfNodeDO n, Set<String> userFields) {
        JsonNode v = tree(n.getApproverValue());
        switch (n.getApproverType()) {
            case WfApprovers.USER -> {
                List<Long> ids = new ArrayList<>();
                if (v.isArray()) v.forEach(x -> Optional.ofNullable(WfConditions.parseLong(x.asText())).ifPresent(ids::add));
                if (ids.isEmpty()) throw BizException.of(SystemErrorCodes.WF_NODE_INCOMPLETE, n.getName());
                Map<Long, UserDTO> users = userApi.list(ids);
                for (Long id : ids) {
                    UserDTO u = users.get(id);
                    if (u == null || !u.enabled()) throw BizException.of(SystemErrorCodes.WF_NODE_USER_DISABLED, n.getName(), u == null ? id : u.realName());
                }
            }
            case WfApprovers.ROLE -> {
                Long roleId = WfConditions.parseLong(v.path("roleId").asText(null));
                if (roleId == null) throw BizException.of(SystemErrorCodes.WF_NODE_INCOMPLETE, n.getName());
                RoleDO r = roleMapper.selectById(roleId);
                if (r == null || r.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.WF_NODE_ROLE_INVALID, n.getName());
            }
            case WfApprovers.BIZ_USER -> {
                if (!v.isTextual() || v.asText().isBlank()) throw BizException.of(SystemErrorCodes.WF_NODE_INCOMPLETE, n.getName());
                if (!userFields.contains(v.asText())) throw BizException.of(SystemErrorCodes.WF_NODE_BIZ_FIELD, n.getName(), v.asText());
            }
            default -> {
            }
        }
    }

    // ==================== 引擎使用的模型 ====================

    /** 已解析的流程版本：分支按优先级排序，节点按顺序 */
    public record Model(WfDefinitionDO definition, List<Branch> branches) {
        public record Branch(Long id, int priority, String name, boolean isDefault, List<WfConditions.Cond> conditions,
                             String conditionsJson, List<WfNodeDO> nodes) {
        }

        public Branch branch(Long id) {
            return branches.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
        }
    }

    Model load(WfDefinitionDO d) {
        List<WfBranchDO> branches = branchMapper.selectList(new LambdaQueryWrapper<WfBranchDO>().eq(WfBranchDO::getDefinitionId, d.getId()));
        Map<Long, List<WfNodeDO>> nodes = branches.isEmpty() ? Map.of() : nodeMapper.selectList(new LambdaQueryWrapper<WfNodeDO>()
                        .in(WfNodeDO::getBranchId, branches.stream().map(WfBranchDO::getId).toList()))
                .stream().collect(Collectors.groupingBy(WfNodeDO::getBranchId));
        List<Model.Branch> list = branches.stream()
                .sorted(Comparator.comparing(WfBranchDO::getPriority).thenComparing(WfBranchDO::getId))
                .map(b -> new Model.Branch(b.getId(), b.getPriority(), b.getName(), Boolean.TRUE.equals(b.getIsDefault()), conditions(b.getConditions()),
                        b.getConditions(), nodes.getOrDefault(b.getId(), List.of()).stream().sorted(Comparator.comparing(WfNodeDO::getSeq)).toList()))
                .toList();
        return new Model(d, list);
    }

    Model load(Long definitionId) {
        return load(getDef(definitionId));
    }

    Optional<WfDefinitionDO> activeOf(String bizType) {
        return find(bizType, WfDefinitionDO.ACTIVE);
    }

    Optional<WfBizTypeDO> bizTypeOpt(String bizType) {
        return Optional.ofNullable(bizTypeMapper.selectOne(new LambdaQueryWrapper<WfBizTypeDO>().eq(WfBizTypeDO::getBizType, bizType)
                .eq(WfBizTypeDO::getActive, true)));
    }

    WfBizTypeDO bizType(String bizType) {
        return bizTypeOpt(bizType).orElseThrow(() -> BizException.of(SystemErrorCodes.WF_BIZ_TYPE_NOT_EXISTS, bizType));
    }

    Map<String, WfBizTypeDO> bizTypeMap() {
        return bizTypeMapper.selectList(new LambdaQueryWrapper<>()).stream().collect(Collectors.toMap(WfBizTypeDO::getBizType, t -> t));
    }

    Map<String, FieldType> fieldTypes(WfBizTypeDO type) {
        Map<String, FieldType> r = new HashMap<>();
        tree(type.getFields()).forEach(f -> {
            try {
                r.put(f.path("code").asText(), FieldType.valueOf(f.path("type").asText()));
            } catch (IllegalArgumentException ignored) {
                // 未知类型忽略
            }
        });
        return r;
    }

    // ==================== 视图 ====================

    private DefinitionView view(WfDefinitionDO d) {
        Model m = load(d);
        List<Long> userIds = new ArrayList<>();
        Set<Long> roleIds = new HashSet<>();
        m.branches().forEach(b -> b.nodes().forEach(n -> {
            JsonNode v = tree(n.getApproverValue());
            if (WfApprovers.USER.equals(n.getApproverType()) && v.isArray()) v.forEach(x -> Optional.ofNullable(WfConditions.parseLong(x.asText())).ifPresent(userIds::add));
            if (WfApprovers.ROLE.equals(n.getApproverType())) Optional.ofNullable(WfConditions.parseLong(v.path("roleId").asText(null))).ifPresent(roleIds::add);
        }));
        if (d.getPublishedBy() != null) userIds.add(d.getPublishedBy());
        Map<Long, UserDTO> users = userApi.list(userIds);
        Map<Long, String> roles = roleIds.isEmpty() ? Map.of() : roleMapper.selectBatchIds(roleIds).stream().collect(Collectors.toMap(RoleDO::getId, RoleDO::getName));
        Map<String, String> userFieldNames = new HashMap<>();
        bizTypeOpt(d.getBizType()).ifPresent(t -> tree(t.getUserFields()).forEach(f -> userFieldNames.put(f.path("code").asText(), f.path("name").asText())));
        List<BranchView> branches = m.branches().stream().map(b -> new BranchView(b.id(), b.priority(), b.name(), b.isDefault(),
                b.conditions().stream().map(c -> new Condition(c.field(), c.op(), c.value())).toList(),
                b.nodes().stream().map(n -> new NodeView(n.getId(), n.getSeq(), n.getName(), n.getApproverType(), tree(n.getApproverValue()),
                        n.getMultiMode(), summary(n, users, roles, userFieldNames))).toList())).toList();
        return new DefinitionView(d.getId(), d.getBizType(), d.getDefVersion(), d.getStatus(), Boolean.TRUE.equals(d.getEnabled()),
                Boolean.TRUE.equals(d.getSkipInitiator()), Boolean.TRUE.equals(d.getSkipDuplicate()), d.getEmptyPolicy(), d.getBasedOn(),
                d.getPublishedAt(), nameOf(users, d.getPublishedBy()), d.getRemark(), branches);
    }

    /** 审批人摘要：如“张三、李四”“销售总监（本公司）”“部门负责人” */
    private String summary(WfNodeDO n, Map<Long, UserDTO> users, Map<Long, String> roles, Map<String, String> userFields) {
        JsonNode v = tree(n.getApproverValue());
        return switch (n.getApproverType()) {
            case WfApprovers.USER -> {
                List<String> names = new ArrayList<>();
                if (v.isArray()) v.forEach(x -> {
                    UserDTO u = users.get(WfConditions.parseLong(x.asText()));
                    names.add(u == null ? "（已删除）" : u.enabled() ? u.realName() : u.realName() + "（已停用）");
                });
                yield names.isEmpty() ? "未指定" : String.join("、", names);
            }
            case WfApprovers.ROLE -> {
                String name = roles.getOrDefault(WfConditions.parseLong(v.path("roleId").asText(null)), "未指定角色");
                yield name + (v.path("sameCompany").asBoolean(true) ? "（发起人所在公司）" : "");
            }
            case WfApprovers.DEPT_LEADER -> "部门负责人";
            case WfApprovers.UPPER_DEPT_LEADER -> "上级部门负责人";
            case WfApprovers.SUPERIOR -> "直属上级";
            case WfApprovers.BIZ_USER -> "单据字段：" + userFields.getOrDefault(v.asText(), v.asText());
            default -> n.getApproverType();
        };
    }

    // ==================== 工具 ====================

    private Optional<WfDefinitionDO> find(String bizType, String status) {
        return Optional.ofNullable(definitionMapper.selectOne(new LambdaQueryWrapper<WfDefinitionDO>().eq(WfDefinitionDO::getBizType, bizType)
                .eq(WfDefinitionDO::getStatus, status).orderByDesc(WfDefinitionDO::getDefVersion).last("LIMIT 1")));
    }

    WfDefinitionDO getDef(Long id) {
        WfDefinitionDO d = id == null ? null : definitionMapper.selectById(id);
        if (d == null) throw BizException.of(SystemErrorCodes.WF_DEFINITION_NOT_EXISTS);
        return d;
    }

    private int nextVersion(String bizType) {
        WfDefinitionDO last = definitionMapper.selectOne(new LambdaQueryWrapper<WfDefinitionDO>().eq(WfDefinitionDO::getBizType, bizType)
                .orderByDesc(WfDefinitionDO::getDefVersion).last("LIMIT 1"));
        return last == null ? 1 : last.getDefVersion() + 1;
    }

    private WfBranchDO insertBranch(Long defId, int priority, String name, boolean isDefault, String conditions) {
        WfBranchDO b = new WfBranchDO();
        b.setDefinitionId(defId);
        b.setPriority(priority);
        b.setName(name);
        b.setIsDefault(isDefault);
        b.setConditions(conditions);
        branchMapper.insert(b);
        return b;
    }

    private void insertNode(Long branchId, int seq, String name, String type, String value, String mode) {
        WfNodeDO n = new WfNodeDO();
        n.setBranchId(branchId);
        n.setSeq(seq);
        n.setName(name);
        n.setApproverType(type);
        n.setApproverValue(value);
        n.setMultiMode(mode);
        nodeMapper.insert(n);
    }

    List<WfConditions.Cond> conditions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WfConditions.Cond>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("[审批流] 条件 JSON 无法解析: {}", json);
            return List.of();
        }
    }

    JsonNode tree(String json) {
        if (json == null || json.isBlank()) return JsonNodeFactory.instance.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return JsonNodeFactory.instance.textNode(json);
        }
    }

    String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nameOf(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }
}
