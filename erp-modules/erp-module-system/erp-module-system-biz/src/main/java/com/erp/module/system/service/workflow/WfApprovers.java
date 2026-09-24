package com.erp.module.system.service.workflow;

import com.erp.common.enums.EnableStatus;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.dal.dataobject.RoleDO;
import com.erp.module.system.dal.mapper.RoleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 审批人解析（需求 08 第 1.2 节）：只返回启用用户，去重并保持顺序 */
@Component
public class WfApprovers {

    public static final String USER = "USER";
    public static final String ROLE = "ROLE";
    public static final String DEPT_LEADER = "DEPT_LEADER";
    public static final String UPPER_DEPT_LEADER = "UPPER_DEPT_LEADER";
    public static final String SUPERIOR = "SUPERIOR";
    public static final String BIZ_USER = "BIZ_USER";
    public static final Set<String> TYPES = Set.of(USER, ROLE, DEPT_LEADER, UPPER_DEPT_LEADER, SUPERIOR, BIZ_USER);
    /** 只解析出一个人的类型（节点表单隐藏“或签/会签”） */
    public static final Set<String> SINGLE = Set.of(DEPT_LEADER, UPPER_DEPT_LEADER, SUPERIOR, BIZ_USER);

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final RoleMapper roleMapper;
    private final ParamApi paramApi;

    public WfApprovers(UserApi userApi, OrgApi orgApi, RoleMapper roleMapper, ParamApi paramApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.roleMapper = roleMapper;
        this.paramApi = paramApi;
    }

    /**
     * @param initiatorId     发起人
     * @param initiatorDeptId 发起人主部门（发起时快照）
     * @param bizUsers        单据用户字段
     */
    public List<Long> resolve(String type, JsonNode value, Long initiatorId, Long initiatorDeptId, Map<String, Long> bizUsers) {
        Set<Long> ids = new LinkedHashSet<>();
        switch (type) {
            case USER -> {
                List<Long> wanted = new ArrayList<>();
                if (value != null && value.isArray()) value.forEach(v -> {
                    Long id = WfConditions.parseLong(v.asText());
                    if (id != null) wanted.add(id);
                });
                Map<Long, UserDTO> users = userApi.list(wanted);
                wanted.stream().map(users::get).filter(u -> u != null && u.enabled()).forEach(u -> ids.add(u.id()));
            }
            case ROLE -> {
                Long roleId = value == null ? null : WfConditions.parseLong(value.path("roleId").asText(null));
                boolean sameCompany = value == null || !value.has("sameCompany") || value.path("sameCompany").asBoolean(true);
                RoleDO role = roleId == null ? null : roleMapper.selectById(roleId);
                if (role != null && role.getStatus() == EnableStatus.ENABLED) {
                    Long company = sameCompany ? companyOf(initiatorDeptId) : null;
                    userApi.listByRole(roleId).stream()
                            .filter(u -> !sameCompany || company == null || company.equals(companyOf(u.deptId())))
                            .forEach(u -> ids.add(u.id()));
                }
            }
            case DEPT_LEADER -> leaderFrom(initiatorDeptId).ifPresent(ids::add);
            case UPPER_DEPT_LEADER -> {
                Long parent = initiatorDeptId == null ? null : orgApi.get(initiatorDeptId).map(OrgDTO::parentId).orElse(null);
                leaderFrom(parent).ifPresent(ids::add);
            }
            case SUPERIOR -> userApi.getSuperior(initiatorId).filter(UserDTO::enabled).ifPresent(u -> ids.add(u.id()));
            case BIZ_USER -> {
                Long id = bizUsers == null || value == null ? null : bizUsers.get(value.asText());
                if (id != null) userApi.get(id).filter(UserDTO::enabled).ifPresent(u -> ids.add(u.id()));
            }
            default -> {
            }
        }
        return new ArrayList<>(ids);
    }

    /** 从该部门开始向上查找第一个有（启用的）负责人的部门 */
    public Optional<Long> leaderFrom(Long deptId) {
        Long cur = deptId;
        for (int depth = 0; cur != null && depth < 50; depth++) {
            Optional<OrgDTO> org = orgApi.get(cur);
            if (org.isEmpty()) return Optional.empty();
            Long leader = org.get().leaderUserId();
            if (leader != null) {
                Optional<UserDTO> u = userApi.get(leader).filter(UserDTO::enabled);
                if (u.isPresent()) return Optional.of(u.get().id());
            }
            cur = org.get().parentId();
        }
        return Optional.empty();
    }

    /** 流程管理员（系统参数 sys.workflow.admin-user-ids），只取启用用户 */
    public List<Long> admins() {
        List<Long> ids = paramApi.getUserIds("sys.workflow.admin-user-ids");
        Map<Long, UserDTO> users = userApi.list(ids);
        return ids.stream().filter(id -> users.containsKey(id) && users.get(id).enabled()).distinct().toList();
    }

    private Long companyOf(Long deptId) {
        return deptId == null ? null : orgApi.getCompanyOf(deptId).map(OrgDTO::id).orElse(null);
    }
}
