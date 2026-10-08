package com.erp.module.shipping.service;

import com.erp.common.exception.BizException;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
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

/** 出货共用：用户 / 客户 / 物料 / 仓库解析、编号、日志、参数、通知、JSON */
@Component("shpSupport")
public class ShpSupport {

    /** 字段权限：金额、单价（11-04 R05） */
    public static final String PRICE_PERMISSION = "shp:document:price";

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final CustomerApi customerApi;
    private final WarehouseApi warehouseApi;
    private final CurrencyApi currencyApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final DictApi dictApi;
    private final ObjectMapper objectMapper;
    private final FileApi fileApi;

    public ShpSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, CustomerApi customerApi,
                      WarehouseApi warehouseApi, CurrencyApi currencyApi, ParamApi paramApi, NotifyApi notifyApi, DictApi dictApi, ObjectMapper objectMapper,
                      FileApi fileApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.customerApi = customerApi;
        this.warehouseApi = warehouseApi;
        this.currencyApi = currencyApi;
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

    public boolean canSeePrice() {
        return hasPermission(PRICE_PERMISSION);
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? new HashMap<>() : new HashMap<>(userApi.list(set));
    }

    public static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    /** 打印签名栏公共字段：所属公司（打印抬头）、制单人、业务负责人、部门；审核人留空手签 */
    public void putPrintSignature(java.util.Map<String, Object> data, com.erp.framework.mybatis.BaseDocDO d) {
        if (d == null) return;
        data.put("orgId", d.getOrgId());
        data.put("createdByName", java.util.Objects.toString(userName(d.getCreatedBy()), ""));
        data.putIfAbsent("ownerName", java.util.Objects.toString(userName(d.getOwnerId()), ""));
        data.put("deptName", d.getDeptId() == null ? "" : orgApi.get(d.getDeptId()).map(o -> o.name()).orElse(""));
        data.putIfAbsent("auditByName", "");
    }

    public String userName(Long id) {
        return id == null ? null : userApi.get(id).map(UserDTO::realName).orElse(null);
    }

    public List<Long> usersWithPermission(String permission) {
        return userApi.listByPermission(permission).stream().filter(UserDTO::enabled).map(UserDTO::id).toList();
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

    public OrgDTO company(Long orgId) {
        return orgId == null ? null : orgApi.get(orgId).orElse(null);
    }

    // ==================== 物料、客户、仓库 ====================

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
        return materialApi.getMaterial(id).orElseThrow(() -> BizException.of(ShippingErrorCodes.NOT_EXISTS, "物料"));
    }

    public CustomerApi customerApi() {
        return customerApi;
    }

    public Map<Long, CustomerDTO> customers(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? new HashMap<>() : new HashMap<>(customerApi.getCustomers(set));
    }

    public CustomerDTO customer(Long id) {
        return customerApi.getCustomer(id).orElseThrow(() -> BizException.of(ShippingErrorCodes.NOT_EXISTS, "客户"));
    }

    public static String customerName(CustomerDTO c) {
        return c == null ? null : StringUtils.hasText(c.shortName()) ? c.shortName() : c.name();
    }

    public Map<Long, WarehouseDTO> warehouses(Collection<Long> ids) {
        Map<Long, WarehouseDTO> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) warehouseApi.get(id).ifPresent(w -> map.put(id, w));
        return map;
    }

    public WarehouseApi warehouseApi() {
        return warehouseApi;
    }

    public String warehouseName(Long id) {
        return id == null ? null : warehouseApi.get(id).map(WarehouseDTO::name).orElse(null);
    }

    public CurrencyApi currencyApi() {
        return currencyApi;
    }

    /** 收货地址快照转文本 */
    public String addressText(String snapshot) {
        AddressDTO a = fromJson(snapshot, new TypeReference<AddressDTO>() {
        });
        return a == null ? null : addressText(a);
    }

    public AddressDTO address(String snapshot) {
        return fromJson(snapshot, new TypeReference<AddressDTO>() {
        });
    }

    public static String addressText(AddressDTO a) {
        StringBuilder sb = new StringBuilder();
        sb.append(Objects.toString(a.companyName(), ""));
        if (StringUtils.hasText(a.contactName())) sb.append(" / ").append(a.contactName());
        if (StringUtils.hasText(a.phone())) sb.append(" ").append(a.phone());
        if (StringUtils.hasText(a.addressLine())) sb.append("，").append(a.addressLine());
        for (String s : new String[]{a.city(), a.province(), a.zip(), a.country()}) {
            if (StringUtils.hasText(s)) sb.append(", ").append(s);
        }
        return sb.toString();
    }

    // ==================== 编号、日志、字典 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public String dictLabel(String type, String value) {
        return value == null ? null : dictApi.label(type, value);
    }

    public static String requireText(String text, String what) {
        if (!StringUtils.hasText(text)) throw BizException.of(ShippingErrorCodes.REASON_REQUIRED, what);
        return text.trim();
    }

    public static String limit(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ==================== 参数、通知、附件 ====================

    public ParamApi params() {
        return paramApi;
    }

    public void message(Collection<Long> userIds, String title, String content, String route) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        notifyApi.message(new MessageSendEvent(ids, MessageSendEvent.Type.REMIND, title, content, route, false));
    }

    public void bindFiles(Collection<Long> fileIds, String bizType, Long bizId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        fileApi.bind(fileIds, bizType, bizId);
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

    public static String batchKey(Long noticeLineId, String batchNo) {
        return noticeLineId + "|" + Objects.toString(batchNo, "");
    }
}
