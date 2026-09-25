package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.DocStateMachines;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.credit.CreditCheckPoint;
import com.erp.module.crm.api.credit.CreditCheckResult;
import com.erp.module.crm.api.credit.CreditUsage;
import com.erp.module.crm.api.credit.CreditUsageProvider;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.crm.controller.vo.CreditVOs.ChangeRow;
import com.erp.module.crm.controller.vo.CreditVOs.ChangeSave;
import com.erp.module.crm.controller.vo.CreditVOs.CreditQuery;
import com.erp.module.crm.controller.vo.CreditVOs.CreditRow;
import com.erp.module.crm.dal.dataobject.CreditChangeDO;
import com.erp.module.crm.dal.dataobject.CustomerCreditDO;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.mapper.CreditChangeMapper;
import com.erp.module.crm.dal.mapper.CustomerCreditMapper;
import com.erp.module.crm.dal.mapper.CustomerMapper;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户信用（需求 03-03）：占用 = 应收余额 + 未出货订单（由财务、销售实现 {@link CreditUsageProvider}，
 * 通过 {@link CreditApi#refresh} 或每天 01:00 全量重算）；检查按客户控制方式（DEFAULT 取系统参数）；
 * 额度调整单审批（CRM_CREDIT_CHANGE）通过后更新客户，临时额度到期次日 00:30 自动恢复。
 */
@Service("crmCreditService")
public class CreditService implements CreditApi {

    public static final String BIZ_TYPE = CrmModuleConfig.CREDIT_CHANGE;
    static final Set<String> CONTROLS = Set.of("DEFAULT", "NONE", "WARN", "BLOCK");
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final CustomerMapper customerMapper;
    private final CustomerCreditMapper creditMapper;
    private final CreditChangeMapper changeMapper;
    private final CustomerService customerService;
    private final CrmSupport support;
    private final WorkflowApi workflowApi;
    private final NotifyApi notifyApi;
    private final List<CreditUsageProvider> providers;

    public CreditService(CustomerMapper customerMapper, CustomerCreditMapper creditMapper, CreditChangeMapper changeMapper,
                         CustomerService customerService, CrmSupport support, WorkflowApi workflowApi, NotifyApi notifyApi,
                         List<CreditUsageProvider> providers) {
        this.customerMapper = customerMapper;
        this.creditMapper = creditMapper;
        this.changeMapper = changeMapper;
        this.customerService = customerService;
        this.support = support;
        this.workflowApi = workflowApi;
        this.notifyApi = notifyApi;
        this.providers = providers;
    }

    // ==================== 占用 ====================

    /** 重新计算占用：各提供方的同类项相加 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refresh(Collection<Long> customerIds) {
        Set<Long> ids = customerIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return;
        Map<Long, BigDecimal[]> sums = new HashMap<>();
        for (CreditUsageProvider p : providers) {
            p.usage(ids).forEach((id, u) -> {
                BigDecimal[] s = sums.computeIfAbsent(id, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                if (u.receivableBalance() != null) s[0] = s[0].add(u.receivableBalance());
                if (u.overdueAmount() != null) s[1] = s[1].add(u.overdueAmount());
                if (u.openOrderAmount() != null) s[2] = s[2].add(u.openOrderAmount());
            });
        }
        Map<Long, CustomerCreditDO> existing = creditMapper.selectList(new LambdaQueryWrapper<CustomerCreditDO>().in(CustomerCreditDO::getCustomerId, ids))
                .stream().collect(Collectors.toMap(CustomerCreditDO::getCustomerId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            BigDecimal[] s = sums.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            CustomerCreditDO c = existing.get(id);
            boolean creating = c == null;
            if (creating) {
                c = new CustomerCreditDO();
                c.setCustomerId(id);
            }
            c.setReceivableBalance(s[0]);
            c.setOverdueAmount(s[1]);
            c.setOpenOrderAmount(s[2]);
            c.setRefreshedAt(now);
            if (creating) creditMapper.insert(c);
            else creditMapper.updateByIdOrFail(c);
        }
    }

    /** 每天 01:00 全量重算（防止回调遗漏造成偏差） */
    @ErpJob(code = "CRM_CREDIT_REFRESH", name = "客户信用占用重算", cron = "0 0 1 * * ?")
    public String refreshAll() {
        List<Long> ids = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>().select(CustomerDO::getId)
                .in(CustomerDO::getCustomerStatus, CustomerStatus.ACTIVE, CustomerStatus.DISABLED, CustomerStatus.BLACKLIST))
                .stream().map(CustomerDO::getId).toList();
        for (int i = 0; i < ids.size(); i += 500) refresh(ids.subList(i, Math.min(i + 500, ids.size())));
        return "重算 " + ids.size() + " 个客户";
    }

    private CustomerCreditDO usageOf(Long customerId) {
        return creditMapper.selectOne(new LambdaQueryWrapper<CustomerCreditDO>().eq(CustomerCreditDO::getCustomerId, customerId));
    }

    /** 实际控制方式：客户为 DEFAULT 时取系统参数 */
    String effectiveControl(CustomerDO c) {
        String mode = c.getCreditControl();
        if (mode == null || "DEFAULT".equals(mode)) mode = support.param().getString(CrmModuleConfig.P_CREDIT_MODE);
        return CONTROLS.contains(mode) ? mode : "WARN";
    }

    // ==================== 检查 ====================

    /**
     * R01～R03、R06。订单检查：应收余额 + 未出货订单 + 本次金额 与额度比较；
     * 出货检查：本次出货对应的订单已包含在未出货订单中，只比较 应收余额 + 本次出货金额。
     */
    @Override
    public CreditCheckResult check(Long customerId, BigDecimal amountBase, CreditCheckPoint checkPoint) {
        CustomerDO c = customerService.getOrThrow(customerId);
        String mode = effectiveControl(c);
        String points = support.param().getString(CrmModuleConfig.P_CREDIT_POINTS);
        boolean pointEnabled = checkPoint == null || points == null
                || Arrays.stream(points.split(",")).map(String::trim).anyMatch(p -> p.equalsIgnoreCase(checkPoint.name()));
        CustomerCreditDO u = usageOf(customerId);
        BigDecimal receivable = u == null ? BigDecimal.ZERO : u.getReceivableBalance();
        BigDecimal open = u == null ? BigDecimal.ZERO : u.getOpenOrderAmount();
        BigDecimal overdue = u == null ? BigDecimal.ZERO : u.getOverdueAmount();
        BigDecimal used = checkPoint == CreditCheckPoint.SHIPMENT ? receivable : receivable.add(open);
        BigDecimal limit = c.getCreditLimit();
        BigDecimal available = limit == null ? null : limit.subtract(used);
        if ("NONE".equals(mode) || !pointEnabled) return new CreditCheckResult(true, "NONE", limit, used, available, overdue, null);
        BigDecimal amount = amountBase == null ? BigDecimal.ZERO : amountBase;
        String name = c.getShortName();
        // R03：逾期应收
        if (overdue.signum() > 0) {
            String msg = "客户「" + name + "」有逾期应收 " + money(overdue) + "，请先催收";
            return new CreditCheckResult(!"BLOCK".equals(mode), mode, limit, used, available, overdue, msg);
        }
        // R02：未设置额度只检查逾期
        if (limit == null) return new CreditCheckResult(true, mode, null, used, null, overdue, null);
        BigDecimal over = used.add(amount).subtract(limit);
        if (over.signum() <= 0) return new CreditCheckResult(true, mode, limit, used, available, overdue, null);
        String msg = "客户「" + name + "」信用额度不足：额度 " + money(limit) + "，已用 " + money(used) + "，本次 " + money(amount) + "，超出 " + money(over);
        return new CreditCheckResult(!"BLOCK".equals(mode), mode, limit, used, available, overdue, msg);
    }

    static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP));
    }

    // ==================== 客户信用列表 ====================

    public PageResult<CreditRow> page(CreditQuery q) {
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getDeleted, false)
                .eq(q.getCustomerId() != null, CustomerDO::getId, q.getCustomerId())
                .eq(q.getOwnerId() != null, CustomerDO::getOwnerId, q.getOwnerId())
                .in(CustomerDO::getCustomerStatus, CustomerStatus.ACTIVE, CustomerStatus.DISABLED, CustomerStatus.BLACKLIST, CustomerStatus.PENDING,
                        CustomerStatus.PROSPECT)
                .orderByAsc(CustomerDO::getCode);
        List<CustomerDO> customers = customerMapper.selectScopedList(w);
        Map<Long, CustomerCreditDO> usage = customers.isEmpty() ? Map.of()
                : creditMapper.selectList(new LambdaQueryWrapper<CustomerCreditDO>().in(CustomerCreditDO::getCustomerId,
                customers.stream().map(CustomerDO::getId).toList())).stream().collect(Collectors.toMap(CustomerCreditDO::getCustomerId, Function.identity()));
        // 只列出设置了额度或有占用的客户
        List<CustomerDO> relevant = customers.stream().filter(c -> c.getCreditLimit() != null || usage.containsKey(c.getId())).toList();
        Map<Long, CreditChangeDO> pending = pendingChanges(relevant.stream().map(CustomerDO::getId).toList());
        Map<Long, UserDTO> users = support.users(relevant.stream().map(CustomerDO::getOwnerId).toList());
        List<CreditRow> rows = new ArrayList<>();
        for (CustomerDO c : relevant) {
            CustomerCreditDO u = usage.get(c.getId());
            BigDecimal receivable = u == null ? BigDecimal.ZERO : u.getReceivableBalance();
            BigDecimal overdue = u == null ? BigDecimal.ZERO : u.getOverdueAmount();
            BigDecimal open = u == null ? BigDecimal.ZERO : u.getOpenOrderAmount();
            BigDecimal used = receivable.add(open);
            BigDecimal limit = c.getCreditLimit();
            BigDecimal pct = limit == null || limit.signum() == 0 ? null : used.divide(limit, 4, RoundingMode.HALF_UP);
            if (Boolean.TRUE.equals(q.getOverdueOnly()) && overdue.signum() <= 0) continue;
            if (q.getUsageAtLeast() != null && (pct == null || pct.compareTo(BigDecimal.valueOf(q.getUsageAtLeast()).movePointLeft(2)) < 0)) continue;
            CreditChangeDO p = pending.get(c.getId());
            rows.add(new CreditRow(c.getId(), c.getCode(), c.getShortName(), c.getOwnerId(), CrmSupport.name(users, c.getOwnerId()), c.getCreditControl(),
                    effectiveControl(c), limit, receivable, overdue, open, used, limit == null ? null : limit.subtract(used), pct, c.getCreditDays(),
                    u == null ? null : u.getRefreshedAt(), p == null ? null : p.getId(), p == null ? null : p.getDocNo()));
        }
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), rows.size());
        int to = Math.min(from + q.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), rows.size());
    }

    private Map<Long, CreditChangeDO> pendingChanges(Collection<Long> customerIds) {
        if (customerIds.isEmpty()) return Map.of();
        return changeMapper.selectList(new LambdaQueryWrapper<CreditChangeDO>().in(CreditChangeDO::getCustomerId, customerIds)
                        .in(CreditChangeDO::getStatus, DocStatus.DRAFT, DocStatus.PENDING_APPROVAL))
                .stream().collect(Collectors.toMap(CreditChangeDO::getCustomerId, Function.identity(), (a, b) -> a));
    }

    // ==================== 额度调整单 ====================

    /** 新建并提交调整单：发起审批 CRM_CREDIT_CHANGE，未配置审批流时直接生效 */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRow createAndSubmit(ChangeSave req) {
        CustomerDO c = customerService.getVisible(req.customerId());
        CreditChangeDO pending = pendingChanges(List.of(c.getId())).get(c.getId());
        if (pending != null) throw BizException.of(CrmErrorCodes.CREDIT_CHANGE_PENDING, c.getShortName(), pending.getDocNo());
        if (req.expireDate() != null && !req.expireDate().isAfter(LocalDate.now())) throw new BizException(CrmErrorCodes.CREDIT_EXPIRE_DATE);
        String control = StringUtils.hasText(req.newControl()) ? req.newControl() : c.getCreditControl();
        if (!CONTROLS.contains(control)) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "信用控制方式不正确");
        if (req.newDays() != null && req.newDays() < 0) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "信用期不能小于 0");
        CreditChangeDO d = new CreditChangeDO();
        d.setDocNo(docNo(c));
        d.setDocDate(LocalDate.now());
        d.setStatus(DocStatus.DRAFT);
        d.setOwnerId(c.getOwnerId());
        d.setDeptId(c.getDeptId());
        d.setOrgId(c.getOrgId());
        d.setCustomerId(c.getId());
        d.setOldLimit(c.getCreditLimit());
        d.setNewLimit(req.newLimit());
        d.setOldDays(c.getCreditDays());
        d.setNewDays(req.newDays() != null ? req.newDays() : c.getCreditDays());
        d.setOldControl(c.getCreditControl());
        d.setNewControl(control);
        d.setExpireDate(req.expireDate());
        d.setReason(req.reason().trim());
        d.setRestored(false);
        changeMapper.insert(d);
        fire(d, DocAction.SUBMIT, null);
        BigDecimal old = c.getCreditLimit() == null ? BigDecimal.ZERO : c.getCreditLimit();
        Map<String, Object> vars = new HashMap<>();
        vars.put("newLimitBase", req.newLimit());
        vars.put("increaseBase", req.newLimit().subtract(old));
        StartResult r = workflowApi.start(BIZ_TYPE, d.getId(), d.getDocNo(), "信用额度调整 " + c.getShortName(), vars,
                Map.of("ownerId", c.getOwnerId()), support.currentUser());
        if (!r.isStarted()) approve(d);
        return changeRow(d, c, support.users(List.of(Objects.requireNonNullElse(d.getCreatedBy(), 0L))));
    }

    /** 单号 = 客户编码-yyyyMMddHHmm（同一分钟重复时加序号） */
    private String docNo(CustomerDO c) {
        String base = c.getCode() + "-" + LocalDateTime.now().format(NO_TIME);
        String no = base;
        for (int i = 2; changeMapper.selectCount(new LambdaQueryWrapper<CreditChangeDO>().eq(CreditChangeDO::getDocNo, no)) > 0; i++) no = base + "-" + i;
        return no;
    }

    /** 审核生效：更新客户额度、信用期、控制方式，记录客户操作日志（R04） */
    private void approve(CreditChangeDO d) {
        d.setApprovedAt(LocalDateTime.now());
        fire(d, DocAction.APPROVE, null);
        CustomerDO c = customerService.getOrThrow(d.getCustomerId());
        c.setCreditLimit(d.getNewLimit());
        c.setCreditDays(d.getNewDays());
        c.setCreditControl(d.getNewControl());
        customerMapper.updateByIdOrFail(c);
        support.log(CustomerService.BIZ_TYPE, c.getId(), c.getCode(), "CREDIT_CHANGE", "调整信用额度", c.getCustomerStatus().name(),
                c.getCustomerStatus().name(), (d.getOldLimit() == null ? "未设置" : money(d.getOldLimit())) + " → " + money(d.getNewLimit())
                        + (d.getExpireDate() == null ? "" : "（临时，至 " + d.getExpireDate() + "）") + "，" + d.getDocNo());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        CreditChangeDO d = changeMapper.selectById(e.getBizId());
        if (d == null || d.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(d);
            case WITHDRAWN -> fire(d, DocAction.WITHDRAW, null);
            default -> fire(d, DocAction.REJECT, e.getComment());
        }
    }

    /** 作废草稿（驳回、撤回后） */
    @Transactional(rollbackFor = Exception.class)
    public void voidChange(Long id) {
        CreditChangeDO d = getChange(id);
        if (!DocStateMachines.STANDARD.canFire(d.getStatus(), DocAction.VOID)) throw BizException.of(CrmErrorCodes.CREDIT_CHANGE_STATUS, d.getStatus().label());
        fire(d, DocAction.VOID, null);
    }

    /** 重新提交（驳回、撤回后的草稿） */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id) {
        CreditChangeDO d = getChange(id);
        if (d.getStatus() != DocStatus.DRAFT) throw BizException.of(CrmErrorCodes.CREDIT_CHANGE_STATUS, d.getStatus().label());
        CustomerDO c = customerService.getOrThrow(d.getCustomerId());
        fire(d, DocAction.SUBMIT, null);
        BigDecimal old = c.getCreditLimit() == null ? BigDecimal.ZERO : c.getCreditLimit();
        StartResult r = workflowApi.start(BIZ_TYPE, d.getId(), d.getDocNo(), "信用额度调整 " + c.getShortName(),
                Map.of("newLimitBase", d.getNewLimit(), "increaseBase", d.getNewLimit().subtract(old)), Map.of("ownerId", c.getOwnerId()),
                support.currentUser());
        if (!r.isStarted()) approve(d);
    }

    private CreditChangeDO getChange(Long id) {
        CreditChangeDO d = id == null ? null : changeMapper.selectById(id);
        if (d == null) throw new BizException(CrmErrorCodes.CREDIT_CHANGE_NOT_EXISTS);
        DataScopes.check(d.getOrgId(), d.getDeptId(), d.getOwnerId(), "调整单");
        return d;
    }

    private void fire(CreditChangeDO d, DocAction action, String reason) {
        DocStatus from = d.getStatus();
        d.setStatus(DocStateMachines.STANDARD.fire(from, action));
        changeMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, d.getId(), d.getDocNo(), action.name(), action.label(), from.name(), d.getStatus().name(), reason);
    }

    public List<ChangeRow> changes(Long customerId) {
        CustomerDO c = customerService.getVisible(customerId);
        List<CreditChangeDO> list = changeMapper.selectList(new LambdaQueryWrapper<CreditChangeDO>().eq(CreditChangeDO::getCustomerId, customerId)
                .orderByDesc(CreditChangeDO::getId));
        Map<Long, UserDTO> users = support.users(list.stream().map(CreditChangeDO::getCreatedBy).toList());
        return list.stream().map(d -> changeRow(d, c, users)).toList();
    }

    private static ChangeRow changeRow(CreditChangeDO d, CustomerDO c, Map<Long, UserDTO> users) {
        return new ChangeRow(d.getId(), d.getDocNo(), d.getDocDate(), c.getId(), c.getCode(), c.getShortName(), d.getOldLimit(), d.getNewLimit(),
                d.getOldDays(), d.getNewDays(), d.getOldControl(), d.getNewControl(), d.getExpireDate(), Boolean.TRUE.equals(d.getRestored()),
                d.getReason(), d.getStatus().name(), CrmSupport.name(users, d.getCreatedBy()), d.getCreatedAt(), d.getApprovedAt());
    }

    // ==================== 临时额度恢复（R04） ====================

    /** 每天 00:30：到期日已过的临时额度恢复为原额度，并通知负责业务员 */
    @ErpJob(code = "CRM_CREDIT_RESTORE", name = "临时信用额度到期恢复", cron = "0 30 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String restoreExpired() {
        LocalDate today = LocalDate.now();
        List<CreditChangeDO> list = changeMapper.selectList(new LambdaQueryWrapper<CreditChangeDO>().eq(CreditChangeDO::getStatus, DocStatus.APPROVED)
                .eq(CreditChangeDO::getRestored, false).isNotNull(CreditChangeDO::getExpireDate).lt(CreditChangeDO::getExpireDate, today)
                .orderByAsc(CreditChangeDO::getApprovedAt));
        int n = 0;
        for (CreditChangeDO d : list) {
            d.setRestored(true);
            changeMapper.updateByIdOrFail(d);
            // 之后又有新的调整单生效时，以新的为准，不再恢复
            boolean superseded = changeMapper.selectCount(new LambdaQueryWrapper<CreditChangeDO>().eq(CreditChangeDO::getCustomerId, d.getCustomerId())
                    .eq(CreditChangeDO::getStatus, DocStatus.APPROVED).gt(CreditChangeDO::getApprovedAt, d.getApprovedAt())) > 0;
            if (superseded) continue;
            CustomerDO c = customerService.getOrThrow(d.getCustomerId());
            c.setCreditLimit(d.getOldLimit());
            customerMapper.updateByIdOrFail(c);
            support.log(CustomerService.BIZ_TYPE, c.getId(), c.getCode(), "CREDIT_RESTORE", "临时额度到期恢复", c.getCustomerStatus().name(),
                    c.getCustomerStatus().name(), money(d.getNewLimit()) + " → " + (d.getOldLimit() == null ? "未设置" : money(d.getOldLimit())));
            notifyApi.message(new MessageSendEvent(List.of(c.getOwnerId()), MessageSendEvent.Type.REMIND, "临时信用额度已到期：" + c.getShortName(),
                    "临时额度 " + money(d.getNewLimit()) + " 已于 " + d.getExpireDate() + " 到期，恢复为 "
                            + (d.getOldLimit() == null ? "未设置" : money(d.getOldLimit())), "/crm/customer/" + c.getId(), false));
            n++;
        }
        return "恢复 " + n + " 个客户的临时额度";
    }
}
