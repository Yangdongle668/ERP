package com.erp.module.production.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterApi;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
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

/** 生产各单据共用：编号、操作日志、状态流转、用户 / 部门 / 物料 / 工作中心 / 仓库解析、参数、通知 */
@Component("mfgSupport")
public class MfgSupport {

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final WorkCenterApi workCenterApi;
    private final WarehouseApi warehouseApi;
    private final DictApi dictApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final UomApi uomApi;

    public MfgSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, WorkCenterApi workCenterApi,
                      WarehouseApi warehouseApi, DictApi dictApi, ParamApi paramApi, NotifyApi notifyApi, UomApi uomApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.workCenterApi = workCenterApi;
        this.warehouseApi = warehouseApi;
        this.dictApi = dictApi;
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

    /**
     * 数据权限字段：生产单据的 dept_id 为车间（为空取经办人部门），owner_id 为经办人（计划员、班组长）。
     */
    public void fillOwner(BaseDocDO d, Long ownerId, Long deptId) {
        Long owner = ownerId != null ? ownerId : currentUser();
        d.setOwnerId(owner);
        UserDTO u = owner == null ? null : userApi.get(owner).orElse(null);
        Long dept = deptId != null ? deptId : u != null ? u.deptId() : null;
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

    public String deptName(Long id) {
        return orgName(orgs(id == null ? List.of() : List.of(id)), id);
    }

    public Set<Long> deptAndChildren(Long deptId) {
        return orgApi.getSelfAndChildrenIds(deptId);
    }

    public static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }

    // ==================== 物料、工作中心、仓库 ====================

    public MaterialApi materialApi() {
        return materialApi;
    }

    public Map<Long, MaterialDTO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        Map<Long, MaterialDTO> map = new HashMap<>();
        for (MaterialDTO m : materialApi.getMaterials(set)) map.put(m.id(), m);
        return map;
    }

    public MaterialDTO material(Long id) {
        return materialApi.getMaterial(id).orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料"));
    }

    public static String code(Map<Long, MaterialDTO> ms, Long id) {
        MaterialDTO m = id == null ? null : ms.get(id);
        return m == null ? String.valueOf(id) : m.code();
    }

    public WorkCenterApi workCenterApi() {
        return workCenterApi;
    }

    public Map<Long, WorkCenterDTO> workCenters() {
        Map<Long, WorkCenterDTO> map = new HashMap<>();
        for (WorkCenterDTO w : workCenterApi.list()) map.put(w.id(), w);
        return map;
    }

    public WorkCenterDTO workCenter(Long id) {
        return id == null ? null : workCenterApi.get(id).orElse(null);
    }

    public WarehouseApi warehouseApi() {
        return warehouseApi;
    }

    public Map<Long, WarehouseDTO> warehouses(Collection<Long> ids) {
        Map<Long, WarehouseDTO> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) warehouseApi.get(id).ifPresent(w -> map.put(id, w));
        return map;
    }

    public String warehouseName(Long id) {
        return id == null ? null : warehouseApi.get(id).map(WarehouseDTO::name).orElse(null);
    }

    /** 按单位精度向上取整（应领数量、倒冲数量） */
    public BigDecimal roundUp(BigDecimal qty, String uom) {
        int p = uom == null ? 4 : Math.min(uomApi.precision(uom), 4);
        return qty.setScale(p, RoundingMode.CEILING);
    }

    /** 按单位精度四舍五入 */
    public BigDecimal round(BigDecimal qty, String uom) {
        return uom == null ? qty.setScale(4, RoundingMode.HALF_UP) : uomApi.round(qty, uom);
    }

    // ==================== 编号、日志、状态 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    /** 通用状态单据的状态流转：状态机计算 → 乐观锁更新 → 操作日志 */
    public <D extends BaseDocDO> void fire(StateMachine<DocStatus, MfgAction> machine, BaseMapperX<D> mapper, D d, String bizType,
                                           MfgAction action, String reason) {
        DocStatus from = d.getStatus();
        d.setStatus(machine.fire(from, action));
        mapper.updateByIdOrFail(d);
        log(bizType, d.getId(), d.getDocNo(), action.name(), action.label(), from.name(), d.getStatus().name(), reason);
    }

    public static String requireReason(String reason, String action) {
        if (!StringUtils.hasText(reason)) throw BizException.of(ProductionErrorCodes.REASON_REQUIRED, action);
        String r = reason.trim();
        return r.length() > 256 ? r.substring(0, 256) : r;
    }

    public static void requireDraft(BaseDocDO d) {
        if (d.getStatus() == DocStatus.PENDING_APPROVAL) throw new BizException(ProductionErrorCodes.DOC_PENDING);
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(ProductionErrorCodes.DOC_NOT_EDITABLE);
    }

    // ==================== 参数、字典、通知 ====================

    public ParamApi params() {
        return paramApi;
    }

    public BigDecimal paramDecimal(String key) {
        BigDecimal v = paramApi.getDecimal(key);
        return v == null ? BigDecimal.ZERO : v;
    }

    public DictApi dict() {
        return dictApi;
    }

    public String dictLabel(String type, String value) {
        if (value == null) return null;
        String l = dictApi.label(type, value);
        return l == null ? value : l;
    }

    public void message(List<Long> userIds, String title, String content, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.message(new MessageSendEvent(ids, MessageSendEvent.Type.REMIND, title, content, route, false));
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
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    /** 1 + 百分比 / 100 */
    public static BigDecimal onePlusPct(BigDecimal pct) {
        return BigDecimal.ONE.add(nz(pct).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
