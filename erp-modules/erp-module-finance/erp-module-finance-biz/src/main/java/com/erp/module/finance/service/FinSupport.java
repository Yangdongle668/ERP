package com.erp.module.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.receivable.ReceivableBalanceChangedEvent;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 财务共用：用户 / 客户 / 供应商 / 物料解析、编号、日志、参数、币别汇率、会计期间、通知 */
@Component("finSupport")
public class FinSupport {

    public static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final MaterialApi materialApi;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;
    private final CurrencyApi currencyApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final FileApi fileApi;
    private final FinPeriodMapper periodMapper;
    private final DomainEventPublisher eventPublisher;
    private final CreditApi creditApi;

    public FinSupport(UserApi userApi, OrgApi orgApi, CodeRuleApi codeRuleApi, DocLogApi docLogApi, MaterialApi materialApi, CustomerApi customerApi,
                      SupplierApi supplierApi, CurrencyApi currencyApi, ParamApi paramApi, NotifyApi notifyApi, FileApi fileApi, FinPeriodMapper periodMapper,
                      DomainEventPublisher eventPublisher, CreditApi creditApi) {
        this.userApi = userApi;
        this.orgApi = orgApi;
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.materialApi = materialApi;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
        this.currencyApi = currencyApi;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.fileApi = fileApi;
        this.periodMapper = periodMapper;
        this.eventPublisher = eventPublisher;
        this.creditApi = creditApi;
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

    // ==================== 客户、供应商、物料 ====================

    public CustomerApi customerApi() {
        return customerApi;
    }

    public SupplierApi supplierApi() {
        return supplierApi;
    }

    public Map<Long, CustomerDTO> customers(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? new HashMap<>() : new HashMap<>(customerApi.getCustomers(set));
    }

    public CustomerDTO customer(Long id) {
        return customerApi.getCustomer(id).orElseThrow(() -> BizException.of(FinanceErrorCodes.NOT_EXISTS, "客户"));
    }

    public static String customerName(CustomerDTO c) {
        return c == null ? null : StringUtils.hasText(c.shortName()) ? c.shortName() : c.name();
    }

    public Map<Long, SupplierDTO> suppliers(Collection<Long> ids) {
        Map<Long, SupplierDTO> map = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) supplierApi.getSupplier(id).ifPresent(s -> map.put(id, s));
        return map;
    }

    public SupplierDTO supplier(Long id) {
        return supplierApi.getSupplier(id).orElseThrow(() -> BizException.of(FinanceErrorCodes.NOT_EXISTS, "供应商"));
    }

    public Map<Long, MaterialDTO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MaterialDTO> map = new HashMap<>();
        if (set.isEmpty()) return map;
        for (MaterialDTO m : materialApi.getMaterials(set)) map.put(m.id(), m);
        return map;
    }

    public MaterialApi materialApi() {
        return materialApi;
    }

    // ==================== 币别、汇率 ====================

    public String baseCurrency() {
        return currencyApi.getBaseCurrency();
    }

    /** 本位币为 1；外币取指定日期汇率（缺失时由系统模块提示先维护汇率） */
    public BigDecimal rate(String currency, LocalDate date) {
        if (currency == null || currency.equals(baseCurrency())) return BigDecimal.ONE;
        return currencyApi.getRate(currency, date == null ? LocalDate.now() : date);
    }

    /** 业务事件生成单据时取汇率：未维护时返回 0（单据保留草稿，确认时再取汇率），不阻断业务 */
    public BigDecimal rateOrZero(String currency, LocalDate date) {
        try {
            return rate(currency, date);
        } catch (BizException e) {
            return BigDecimal.ZERO;
        }
    }

    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        return Decimals.amount(nz(amount).multiply(rate == null ? BigDecimal.ONE : rate));
    }

    public CurrencyApi currencyApi() {
        return currencyApi;
    }

    // ==================== 会计期间 ====================

    public static String periodOf(LocalDate date) {
        return (date == null ? LocalDate.now() : date).format(PERIOD);
    }

    public boolean isClosed(String period) {
        FinPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, period));
        return p != null && "CLOSED".equals(p.getPeriodStatus());
    }

    /** FIN-AR-R05 / FIN-AP-R06 / FIN-CLS-R03：业务日期所在会计期间未结账 */
    public void requireOpen(LocalDate date) {
        String period = periodOf(date);
        if (isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
    }

    // ==================== 编号、日志、参数、通知、附件 ====================

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public static String batchNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

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

    /** FIN-AR-R06：客户应收余额变化（刷新 CRM 信用占用并发布事件） */
    public void balanceChanged(Collection<Long> customerIds) {
        List<Long> ids = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        creditApi.refresh(ids);
        eventPublisher.publish(new ReceivableBalanceChangedEvent(ids));
    }

    public DomainEventPublisher events() {
        return eventPublisher;
    }

    // ==================== 数值、文本 ====================

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static String plain(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    public static String limit(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static String requireText(String text, String what) {
        if (!StringUtils.hasText(text)) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, what);
        return text.trim();
    }

    /** 价税合计拆分：不含税 = 合计 ÷ (1 + 税率)，税额 = 合计 − 不含税 */
    public static BigDecimal[] split(BigDecimal total, BigDecimal taxRate) {
        BigDecimal t = Decimals.amount(nz(total));
        BigDecimal amount = t.divide(BigDecimal.ONE.add(nz(taxRate)), Decimals.AMOUNT_SCALE, RoundingMode.HALF_UP);
        return new BigDecimal[]{amount, t.subtract(amount), t};
    }
}
