package com.erp.module.pmc.service;

import com.erp.common.exception.BizException;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.uom.UomApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** PMC 共用：用户 / 组织 / 物料解析、编号、日志、参数、通知、数值 */
@Component("pmcSupport")
public class PmcSupport {

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final UomApi uomApi;

    public PmcSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, ParamApi paramApi,
                      NotifyApi notifyApi, UomApi uomApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.uomApi = uomApi;
    }

    // ==================== 用户、组织 ====================

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

    public String userName(Long id) {
        return id == null ? null : userApi.get(id).map(UserDTO::realName).orElse(null);
    }

    public List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
    }

    /** 数据权限字段：计划单据的 dept_id 为经办人部门，owner_id 为经办人（计划员） */
    public void fillOwner(BaseDocDO d) {
        Long owner = currentUser();
        d.setOwnerId(owner);
        UserDTO u = owner == null ? null : userApi.get(owner).orElse(null);
        Long dept = u != null ? u.deptId() : null;
        if (dept == null) {
            LoginUser login = SecurityUtils.getLoginUserOrNull();
            if (login != null) dept = login.deptId();
        }
        d.setDeptId(dept);
        Long fallback = u != null ? u.orgId() : null;
        d.setOrgId(dept == null ? fallback : orgApi.getCompanyOf(dept).map(OrgDTO::id).orElse(fallback));
    }

    public Map<Long, OrgDTO> orgs(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : orgApi.list(set);
    }

    public static String orgName(Map<Long, OrgDTO> orgs, Long id) {
        OrgDTO o = id == null ? null : orgs.get(id);
        return o == null ? null : o.shortName() != null && !o.shortName().isBlank() ? o.shortName() : o.name();
    }

    public Set<Long> deptAndChildren(Long deptId) {
        return orgApi.getSelfAndChildrenIds(deptId);
    }

    // ==================== 物料 ====================

    public MaterialApi materialApi() {
        return materialApi;
    }

    public Map<Long, MaterialDTO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MaterialDTO> map = new HashMap<>();
        if (set.isEmpty()) return map;
        for (MaterialDTO m : materialApi.getMaterials(set)) map.put(m.id(), m);
        return map;
    }

    public MaterialDTO material(Long id) {
        return materialApi.getMaterial(id).orElseThrow(() -> BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "物料"));
    }

    public static String code(Map<Long, MaterialDTO> ms, Long id) {
        MaterialDTO m = id == null ? null : ms.get(id);
        return m == null ? String.valueOf(id) : m.code();
    }

    /** 计划属性（逐个读取，调用方自行缓存） */
    public Map<Long, MaterialPlanAttr> planAttrs(Collection<Long> ids) {
        Map<Long, MaterialPlanAttr> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) {
            MaterialPlanAttr a = materialApi.getPlanAttr(id);
            if (a != null) map.put(id, a);
        }
        return map;
    }

    public Map<Long, MaterialPurchaseAttr> purchaseAttrs(Collection<Long> ids) {
        Map<Long, MaterialPurchaseAttr> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) {
            MaterialPurchaseAttr a = materialApi.getPurchaseAttr(id);
            if (a != null) map.put(id, a);
        }
        return map;
    }

    /** 单位精度（最多 4 位） */
    public int precision(String uom) {
        return uom == null ? 4 : Math.min(uomApi.precision(uom), 4);
    }

    /** 按单位精度向上取整 */
    public BigDecimal roundUp(BigDecimal qty, String uom) {
        int p = uom == null ? 4 : Math.min(uomApi.precision(uom), 4);
        return qty.setScale(p, RoundingMode.CEILING);
    }

    /** 按单位精度向下取整（可开工套数） */
    public BigDecimal roundDown(BigDecimal qty, String uom) {
        int p = uom == null ? 4 : Math.min(uomApi.precision(uom), 4);
        return qty.setScale(p, RoundingMode.FLOOR);
    }

    // ==================== 编号、日志 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public static String requireReason(String reason, String action) {
        if (!StringUtils.hasText(reason)) throw BizException.of(PmcErrorCodes.REASON_REQUIRED, action);
        String r = reason.trim();
        return r.length() > 256 ? r.substring(0, 256) : r;
    }

    // ==================== 参数、通知 ====================

    public ParamApi params() {
        return paramApi;
    }

    public void message(List<Long> userIds, String title, String content, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.message(new MessageSendEvent(ids, MessageSendEvent.Type.REMIND, title, content, route, false));
    }

    public NotifyApi notifyApi() {
        return notifyApi;
    }

    /** 工作台待办（标题含处理内容，如“催料：螺丝 缺 316，需要 10-05”） */
    public void todo(String key, List<Long> userIds, String bizType, Long bizId, String bizNo, String title, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        String t = title.length() > 200 ? title.substring(0, 200) : title;
        notifyApi.todo(new TodoCreatedEvent(key, ids, TodoCreatedEvent.Category.TASK, bizType, bizId, bizNo, t, route, TodoCreatedEvent.Priority.NORMAL, null));
    }

    // ==================== 数值 ====================

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    public static BigDecimal max0(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
