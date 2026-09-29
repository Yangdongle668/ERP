package com.erp.module.quality.service;

import com.erp.common.exception.BizException;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 品质共用：用户 / 物料 / 供应商 / 客户解析、编号、日志、参数、通知、JSON */
@Component("qcSupport")
public class QcSupport {

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final SupplierApi supplierApi;
    private final CustomerApi customerApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final DictApi dictApi;
    private final ObjectMapper objectMapper;
    private final FileApi fileApi;

    public QcSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, SupplierApi supplierApi,
                     CustomerApi customerApi, ParamApi paramApi, NotifyApi notifyApi, DictApi dictApi, ObjectMapper objectMapper, FileApi fileApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.supplierApi = supplierApi;
        this.customerApi = customerApi;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.dictApi = dictApi;
        this.objectMapper = objectMapper;
        this.fileApi = fileApi;
    }

    // ==================== 用户、组织 ====================

    public Long currentUser() {
        return SecurityUtils.getLoginUserIdOrNull();
    }

    public boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? new HashMap<>() : new HashMap<>(userApi.list(set));
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

    /** 品质主管：参数 qc.managers，为空时取拥有“NCR 关闭”权限的用户 */
    public List<Long> managers() {
        List<Long> ids = paramApi.getUserIds(QualityModuleConfig.P_MANAGERS);
        return ids == null || ids.isEmpty() ? usersWithPermission("qc:ncr:close") : ids;
    }

    /** 数据权限字段：经办人及其部门、公司 */
    public void fillOwner(BaseDocDO d, Long ownerId) {
        Long owner = ownerId != null ? ownerId : currentUser();
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

    // ==================== 物料、供应商、客户 ====================

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
        return materialApi.getMaterial(id).orElseThrow(() -> BizException.of(QualityErrorCodes.NOT_EXISTS, "物料"));
    }

    public Map<Long, SupplierDTO> suppliers(Collection<Long> ids) {
        Map<Long, SupplierDTO> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) supplierApi.getSupplier(id).ifPresent(s -> map.put(id, s));
        return map;
    }

    public SupplierApi supplierApi() {
        return supplierApi;
    }

    public Map<Long, CustomerDTO> customers(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? new HashMap<>() : new HashMap<>(customerApi.getCustomers(set));
    }

    public CustomerApi customerApi() {
        return customerApi;
    }

    public static String customerName(CustomerDTO c) {
        return c == null ? null : StringUtils.hasText(c.shortName()) ? c.shortName() : c.name();
    }

    // ==================== 编号、日志、字典 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public DictApi dictApi() {
        return dictApi;
    }

    public String dictLabel(String type, String value) {
        return value == null ? null : dictApi.label(type, value);
    }

    public static String requireText(String text, String what) {
        if (!StringUtils.hasText(text)) throw BizException.of(QualityErrorCodes.REASON_REQUIRED, what);
        return text.trim();
    }

    public static String limit(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ==================== 参数、通知 ====================

    public ParamApi params() {
        return paramApi;
    }

    public void message(Collection<Long> userIds, String title, String content, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.message(new MessageSendEvent(ids, MessageSendEvent.Type.REMIND, title, content, route, false));
    }

    public void todo(String key, Collection<Long> userIds, String bizType, Long bizId, String bizNo, String title, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.todo(new TodoCreatedEvent(key, ids, TodoCreatedEvent.Category.TASK, bizType, bizId, bizNo, limit(title, 200), route,
                TodoCreatedEvent.Priority.NORMAL, null));
    }

    public void todoDone(String key) {
        notifyApi.done(new TodoDoneEvent(key, null, TodoDoneEvent.Result.DONE));
    }

    public void alert(String key, AlertRaisedEvent.Level level, Collection<Long> userIds, String bizType, Long bizId, String title, String content, String route) {
        notifyApi.alert(new AlertRaisedEvent(key, "QUALITY", level, List.copyOf(userIds), null, bizType, bizId, limit(title, 200), content, route));
    }

    public void resolve(String alertKey) {
        notifyApi.resolve(alertKey);
    }

    // ==================== 附件 ====================

    public void bindFiles(Collection<Long> fileIds, String bizType, Long bizId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        fileApi.bind(fileIds, bizType, bizId);
    }

    public FileApi fileApi() {
        return fileApi;
    }

    // ==================== JSON、ID 列表 ====================

    public String toJson(Object o) {
        try {
            return o == null ? null : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> T fromJson(String s, TypeReference<T> type) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return objectMapper.readValue(s, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Long> ids(String text) {
        if (!StringUtils.hasText(text)) return new ArrayList<>();
        return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).map(Long::valueOf).collect(Collectors.toCollection(ArrayList::new));
    }

    public static String idText(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
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
}
