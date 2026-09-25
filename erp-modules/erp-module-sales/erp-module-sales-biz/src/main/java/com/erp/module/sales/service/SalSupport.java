package com.erp.module.sales.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 销售各单据共用：编号、操作日志、状态流转、用户 / 客户 / 物料解析、字段权限、通知 */
@Component
public class SalSupport {

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final CustomerApi customerApi;
    private final CurrencyApi currencyApi;
    private final DictApi dictApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final ObjectMapper objectMapper;

    public SalSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, CustomerApi customerApi,
                      CurrencyApi currencyApi, DictApi dictApi, ParamApi paramApi, NotifyApi notifyApi, ObjectMapper objectMapper) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.customerApi = customerApi;
        this.currencyApi = currencyApi;
        this.dictApi = dictApi;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.objectMapper = objectMapper;
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

    public UserDTO user(Long id) {
        return id == null ? null : userApi.get(id).orElse(null);
    }

    public UserApi userApi() {
        return userApi;
    }

    /** 拥有某权限的启用用户 ID */
    public List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
    }

    /** 单据经办人：业务员及其部门、公司（数据权限字段） */
    public void fillOwner(BaseDocDO d, Long ownerId) {
        Long owner = ownerId != null ? ownerId : currentUser();
        d.setOwnerId(owner);
        UserDTO u = user(owner);
        if (u != null) {
            d.setDeptId(u.deptId());
            d.setOrgId(companyOf(u.deptId(), u.orgId()));
        } else {
            LoginUser login = SecurityUtils.getLoginUserOrNull();
            if (login != null) {
                d.setDeptId(login.deptId());
                d.setOrgId(login.orgId());
            }
        }
    }

    public Long companyOf(Long deptId, Long fallback) {
        if (deptId == null) return fallback;
        return orgApi.getCompanyOf(deptId).map(OrgDTO::id).orElse(fallback);
    }

    public Map<Long, OrgDTO> orgs(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : orgApi.list(set);
    }

    public static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }

    /** 字段权限 sales:order:cost：订单、价格表的成本与毛利 */
    public static boolean canViewOrderCost() {
        return hasPermission(SalesModuleConfig.ORDER_COST);
    }

    /** 字段权限 sales:quotation:cost：报价、RFQ 的成本与毛利 */
    public static boolean canViewQuoteCost() {
        return hasPermission(SalesModuleConfig.QUOTATION_COST);
    }

    public static BigDecimal mask(BigDecimal v, boolean visible) {
        return visible ? v : null;
    }

    // ==================== 客户 ====================

    public CustomerApi customerApi() {
        return customerApi;
    }

    public CustomerDTO customer(Long id) {
        return customerApi.getCustomer(id).orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "客户"));
    }

    public Map<Long, CustomerDTO> customers(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : customerApi.getCustomers(set);
    }

    public static String shortName(Map<Long, CustomerDTO> cs, Long id) {
        CustomerDTO c = id == null ? null : cs.get(id);
        return c == null ? null : c.shortName();
    }

    // ==================== 物料 ====================

    public Map<Long, MaterialDTO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        Map<Long, MaterialDTO> map = new HashMap<>();
        for (MaterialDTO m : materialApi.getMaterials(set)) map.put(m.id(), m);
        return map;
    }

    public MaterialDTO material(Long id) {
        return materialApi.getMaterial(id).orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料"));
    }

    public MaterialApi materialApi() {
        return materialApi;
    }

    /** 换算为基本单位数量 */
    public BigDecimal toBase(Long materialId, BigDecimal qty, String uom) {
        return materialApi.convertToBase(materialId, qty, uom);
    }

    // ==================== 币别 ====================

    public CurrencyApi currencyApi() {
        return currencyApi;
    }

    public String baseCurrency() {
        return currencyApi.getBaseCurrency();
    }

    // ==================== 编号、日志、状态 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public boolean manualCodeAllowed(String rule) {
        return codeRuleApi.isManualAllowed(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    /** 状态流转：状态机计算 → 乐观锁更新 → 单据操作日志 */
    public <D extends BaseDocDO> void fire(StateMachine<DocStatus, SalAction> machine, BaseMapperX<D> mapper, D d, String bizType,
                                           SalAction action, String reason) {
        DocStatus from = d.getStatus();
        d.setStatus(machine.fire(from, action));
        mapper.updateByIdOrFail(d);
        log(bizType, d.getId(), d.getDocNo(), action.name(), action.label(), from.name(), d.getStatus().name(), reason);
    }

    public static String requireReason(String reason, String action) {
        if (!StringUtils.hasText(reason)) throw BizException.of(SalesErrorCodes.REASON_REQUIRED, action);
        return reason.trim();
    }

    public static void requireDraft(BaseDocDO d) {
        if (d.getStatus() == DocStatus.PENDING_APPROVAL) throw new BizException(SalesErrorCodes.DOC_PENDING);
        if (d.getStatus() != DocStatus.DRAFT) throw new BizException(SalesErrorCodes.DOC_NOT_EDITABLE);
    }

    // ==================== 通知 ====================

    public void message(List<Long> userIds, String title, String content, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.message(new MessageSendEvent(ids, MessageSendEvent.Type.REMIND, title, content, route, false));
    }

    public void todo(String key, List<Long> userIds, String bizType, Long bizId, String bizNo, String title, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.todo(new TodoCreatedEvent(key, ids, TodoCreatedEvent.Category.TASK, bizType, bizId, bizNo, title, route,
                TodoCreatedEvent.Priority.NORMAL, null));
    }

    public NotifyApi notifyApi() {
        return notifyApi;
    }

    // ==================== 其他 ====================

    public DictApi dict() {
        return dictApi;
    }

    public ParamApi params() {
        return paramApi;
    }

    public String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> T fromJson(String s, Class<T> type) {
        if (s == null || s.isBlank()) return null;
        try {
            return objectMapper.readValue(s, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static Long nz(Long v) {
        return v == null ? 0L : v;
    }

    /** 数字显示（去掉末尾 0），用于提示文字 */
    public static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 小数比例 → 百分数显示（2 位小数，去末尾 0），如 0.6667 → 66.67 */
    public static String pctText(BigDecimal ratio) {
        return ratio == null ? "" : plain(ratio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP));
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
