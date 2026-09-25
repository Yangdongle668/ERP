package com.erp.module.sales.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.controller.vo.OrderVOs.FollowUpReq;
import com.erp.module.sales.controller.vo.OrderVOs.OrderPaymentSummary;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentPlanQuery;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentPlanRow;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentSummary;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderExecDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalPaymentPlanDO;
import com.erp.module.sales.dal.mapper.SalOrderExecMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalPaymentPlanMapper;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.BaseEvent;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 回款计划（需求 04-07）：订单审核时按付款条件快照生成节点；出货类节点（出货日 / 提单日 / 开票日 / 月结）按每次出货金额拆分出批次计划；
 * 收款先到期先分配；每天重算状态。用户只能录入催收备注。
 */
@Service("salPaymentPlanService")
public class PaymentPlanService {

    public static final String NOT_DUE = "NOT_DUE";
    public static final String DUE = "DUE";
    public static final String OVERDUE = "OVERDUE";
    public static final String PARTIAL = "PARTIAL";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELED = "CANCELED";
    static final List<String> OPEN_STATUSES = List.of(NOT_DUE, DUE, OVERDUE, PARTIAL);
    /** 按出货拆分的节点 */
    static final Set<String> SHIPMENT_EVENTS = Set.of(BaseEvent.SHIPMENT.name(), BaseEvent.BL_DATE.name(), BaseEvent.INVOICE_DATE.name(),
            BaseEvent.MONTH_END.name(), BaseEvent.RECEIPT_DATE.name());
    static final Comparator<SalPaymentPlanDO> ALLOC_ORDER = Comparator.comparing(SalPaymentPlanDO::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SalPaymentPlanDO::getSeq).thenComparing(SalPaymentPlanDO::getBatchNo);

    private final SalPaymentPlanMapper mapper;
    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper lineMapper;
    private final SalOrderExecMapper execMapper;
    private final SalSupport support;

    public PaymentPlanService(SalPaymentPlanMapper mapper, SalOrderMapper orderMapper, SalOrderLineMapper lineMapper, SalOrderExecMapper execMapper,
                              SalSupport support) {
        this.mapper = mapper;
        this.execMapper = execMapper;
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.support = support;
    }

    // ==================== 生成与维护 ====================

    /** 订单审核：按付款条件快照生成节点（补货订单、金额 0 不生成，SAL-PP-R04） */
    void generate(SalOrderDO o) {
        deleteByOrder(o.getId());
        if ("REPLACEMENT".equals(o.getOrderType()) || o.getTotalAmount().signum() == 0) return;
        PaymentTermDTO term = support.fromJson(o.getPaymentTermSnapshot(), PaymentTermDTO.class);
        if (term == null || term.nodes() == null || term.nodes().isEmpty()) return;
        List<PaymentTermDTO.Node> nodes = term.nodes().stream().sorted(Comparator.comparingInt(PaymentTermDTO.Node::seq)).toList();
        BigDecimal rest = o.getTotalAmount();
        LocalDate beforeShipDue = earliestDue(o.getId());
        for (int i = 0; i < nodes.size(); i++) {
            PaymentTermDTO.Node n = nodes.get(i);
            BigDecimal amount = i == nodes.size() - 1 ? rest : round(o.getTotalAmount().multiply(n.percent()), o.getCurrency());
            rest = rest.subtract(amount);
            SalPaymentPlanDO p = new SalPaymentPlanDO();
            p.setOrderId(o.getId());
            p.setSeq(n.seq());
            p.setBatchNo(0);
            p.setNodeName(n.name());
            p.setPercent(n.percent());
            p.setBaseEvent(n.baseEvent().name());
            p.setDays(n.days());
            p.setPlanAmount(amount);
            p.setReceivedAmount(BigDecimal.ZERO);
            if (n.baseEvent() == BaseEvent.ORDER_DATE) {
                p.setEventDate(o.getDocDate());
                p.setDueDate(o.getDocDate().plusDays(n.days()));
            } else if (n.baseEvent() == BaseEvent.BEFORE_SHIPMENT) {
                p.setDueDate(beforeShipDue);
            }
            p.setPlanStatus(status(p, LocalDate.now()));
            mapper.insert(p);
        }
    }

    /** 最早承诺交期（无则要求交期） */
    private LocalDate earliestDue(Long orderId) {
        return lineMapper.selectByParent(orderId).stream().filter(l -> !OrderService.CLOSED.equals(l.getLineStatus()))
                .map(OrderService::dueDate).min(Comparator.naturalOrder()).orElse(null);
    }

    /** 承诺交期变化后：出货前节点到期日 = 最早承诺交期 */
    void refreshBeforeShipment(SalOrderDO o) {
        LocalDate due = earliestDue(o.getId());
        for (SalPaymentPlanDO p : byOrder(o.getId())) {
            if (BaseEvent.BEFORE_SHIPMENT.name().equals(p.getBaseEvent()) && !Objects.equals(p.getDueDate(), due)) {
                p.setDueDate(due);
                p.setPlanStatus(status(p, LocalDate.now()));
                mapper.updateByIdOrFail(p);
            }
        }
    }

    void deleteByOrder(Long orderId) {
        mapper.delete(new LambdaQueryWrapper<SalPaymentPlanDO>().eq(SalPaymentPlanDO::getOrderId, orderId));
    }

    /**
     * 出货确认：出货类节点按本次出货金额 × 比例拆分出“第 n 批”计划；订单全部出齐时最后一批取节点剩余金额。
     *
     * @param shipAmount 本次出货金额（原币含税）
     */
    void onShipped(SalOrderDO o, Long shipmentId, String shipmentNo, LocalDate shipDate, BigDecimal shipAmount, boolean orderFullyShipped) {
        List<SalPaymentPlanDO> plans = byOrder(o.getId());
        Map<Integer, List<SalPaymentPlanDO>> bySeq = plans.stream().collect(Collectors.groupingBy(SalPaymentPlanDO::getSeq));
        for (List<SalPaymentPlanDO> group : bySeq.values()) {
            SalPaymentPlanDO balance = group.stream().filter(p -> p.getBatchNo() == 0).findFirst().orElse(null);
            if (balance == null || !SHIPMENT_EVENTS.contains(balance.getBaseEvent()) || CANCELED.equals(balance.getPlanStatus())) continue;
            BigDecimal amount = orderFullyShipped ? balance.getPlanAmount()
                    : round(shipAmount.multiply(balance.getPercent()), o.getCurrency()).min(balance.getPlanAmount());
            if (amount.signum() <= 0) continue;
            int batch = group.stream().mapToInt(SalPaymentPlanDO::getBatchNo).max().orElse(0) + 1;
            SalPaymentPlanDO p = new SalPaymentPlanDO();
            p.setOrderId(o.getId());
            p.setSeq(balance.getSeq());
            p.setBatchNo(batch);
            p.setNodeName(baseName(balance.getNodeName()) + "-第 " + batch + " 批");
            p.setPercent(balance.getPercent());
            p.setBaseEvent(balance.getBaseEvent());
            p.setDays(balance.getDays());
            p.setPlanAmount(amount);
            p.setShipmentId(shipmentId);
            p.setRemark(null);
            BigDecimal moved = balance.getReceivedAmount().min(amount);
            p.setReceivedAmount(moved);
            balance.setReceivedAmount(balance.getReceivedAmount().subtract(moved));
            String event = balance.getBaseEvent();
            if (BaseEvent.SHIPMENT.name().equals(event) || BaseEvent.RECEIPT_DATE.name().equals(event)) {
                p.setEventDate(shipDate);
                p.setDueDate(shipDate.plusDays(balance.getDays()));
            } else if (BaseEvent.MONTH_END.name().equals(event)) {
                p.setEventDate(shipDate);
                p.setDueDate(shipDate.with(TemporalAdjusters.lastDayOfMonth()).plusDays(balance.getDays()));
            }
            p.setPlanStatus(status(p, LocalDate.now()));
            mapper.insert(p);
            balance.setPlanAmount(balance.getPlanAmount().subtract(amount));
            if (balance.getPlanAmount().signum() == 0 && balance.getReceivedAmount().signum() == 0) {
                mapper.deleteById(balance.getId());
            } else {
                balance.setPlanStatus(status(balance, LocalDate.now()));
                mapper.updateByIdOrFail(balance);
            }
        }
    }

    private static String baseName(String name) {
        int i = name.indexOf("-第 ");
        return i > 0 ? name.substring(0, i) : name;
    }

    /** 出货冲销：删除该出货单的批次计划，金额与已收退回节点余额 */
    void onShipmentReversed(SalOrderDO o, Long shipmentId) {
        List<SalPaymentPlanDO> plans = byOrder(o.getId());
        for (SalPaymentPlanDO b : plans.stream().filter(p -> shipmentId.equals(p.getShipmentId())).toList()) {
            SalPaymentPlanDO balance = plans.stream().filter(p -> p.getSeq().equals(b.getSeq()) && p.getBatchNo() == 0).findFirst().orElse(null);
            if (balance == null) {
                balance = new SalPaymentPlanDO();
                balance.setOrderId(o.getId());
                balance.setSeq(b.getSeq());
                balance.setBatchNo(0);
                balance.setNodeName(baseName(b.getNodeName()));
                balance.setPercent(b.getPercent());
                balance.setBaseEvent(b.getBaseEvent());
                balance.setDays(b.getDays());
                balance.setPlanAmount(b.getPlanAmount());
                balance.setReceivedAmount(b.getReceivedAmount());
                balance.setPlanStatus(status(balance, LocalDate.now()));
                mapper.insert(balance);
                plans.add(balance);
            } else {
                balance.setPlanAmount(balance.getPlanAmount().add(b.getPlanAmount()));
                balance.setReceivedAmount(balance.getReceivedAmount().add(b.getReceivedAmount()));
                if (CANCELED.equals(balance.getPlanStatus())) balance.setPlanStatus(NOT_DUE);
                balance.setPlanStatus(status(balance, LocalDate.now()));
                mapper.updateByIdOrFail(balance);
            }
            mapper.deleteById(b.getId());
        }
    }

    /** 提单日期回填：该出货单的“提单日”类批次计划计算到期日 */
    void onBillOfLading(Long shipmentId, LocalDate blDate) {
        for (SalPaymentPlanDO p : mapper.selectList(new LambdaQueryWrapper<SalPaymentPlanDO>().eq(SalPaymentPlanDO::getShipmentId, shipmentId)
                .eq(SalPaymentPlanDO::getBaseEvent, BaseEvent.BL_DATE.name()))) {
            p.setEventDate(blDate);
            p.setDueDate(blDate.plusDays(p.getDays()));
            p.setPlanStatus(status(p, LocalDate.now()));
            mapper.updateByIdOrFail(p);
        }
    }

    /** 开票：尚无事件日期的“开票日”类批次计划计算到期日 */
    void onInvoiced(Long orderId, LocalDate invoiceDate) {
        for (SalPaymentPlanDO p : byOrder(orderId)) {
            if (BaseEvent.INVOICE_DATE.name().equals(p.getBaseEvent()) && p.getBatchNo() > 0 && p.getEventDate() == null) {
                p.setEventDate(invoiceDate);
                p.setDueDate(invoiceDate.plusDays(p.getDays()));
                p.setPlanStatus(status(p, LocalDate.now()));
                mapper.updateByIdOrFail(p);
            }
        }
    }

    /**
     * 收款分配（原币）：正数按先到期先分配（没有到期日的排在最后），最后一个节点可超收；负数从最后分配的节点扣回。
     *
     * @return 实际分配金额
     */
    BigDecimal allocate(Long orderId, BigDecimal amount) {
        List<SalPaymentPlanDO> plans = byOrder(orderId).stream().filter(p -> !CANCELED.equals(p.getPlanStatus())).sorted(ALLOC_ORDER).toList();
        if (plans.isEmpty() || amount.signum() == 0) return BigDecimal.ZERO;
        BigDecimal rest = amount;
        Set<SalPaymentPlanDO> touched = new HashSet<>();
        if (amount.signum() > 0) {
            for (SalPaymentPlanDO p : plans) {
                if (rest.signum() <= 0) break;
                BigDecimal room = p.getPlanAmount().subtract(p.getReceivedAmount());
                if (room.signum() <= 0) continue;
                BigDecimal take = room.min(rest);
                p.setReceivedAmount(p.getReceivedAmount().add(take));
                rest = rest.subtract(take);
                touched.add(p);
            }
            if (rest.signum() > 0) {
                SalPaymentPlanDO last = plans.get(plans.size() - 1);
                last.setReceivedAmount(last.getReceivedAmount().add(rest));
                touched.add(last);
            }
        } else {
            rest = rest.negate();
            for (int i = plans.size() - 1; i >= 0 && rest.signum() > 0; i--) {
                SalPaymentPlanDO p = plans.get(i);
                BigDecimal take = p.getReceivedAmount().min(rest);
                if (take.signum() <= 0) continue;
                p.setReceivedAmount(p.getReceivedAmount().subtract(take));
                rest = rest.subtract(take);
                touched.add(p);
            }
        }
        LocalDate today = LocalDate.now();
        for (SalPaymentPlanDO p : touched) {
            p.setPlanStatus(status(p, today));
            mapper.updateByIdOrFail(p);
        }
        return amount;
    }

    /**
     * 订单变更后按新金额重算（SAL-SC-R05）：未拆分节点金额 = 新金额 × 比例 − 已拆分批次金额（不小于已收）；
     * 尾差调整到最后一个未收齐的节点。
     */
    void recalc(SalOrderDO o) {
        List<SalPaymentPlanDO> plans = byOrder(o.getId());
        if (plans.isEmpty()) {
            if (o.getStatus() == DocStatus.APPROVED || o.getStatus() == DocStatus.IN_PROGRESS) generate(o);
            return;
        }
        Map<Integer, List<SalPaymentPlanDO>> bySeq = plans.stream().collect(Collectors.groupingBy(SalPaymentPlanDO::getSeq));
        for (List<SalPaymentPlanDO> group : bySeq.values()) {
            SalPaymentPlanDO balance = group.stream().filter(p -> p.getBatchNo() == 0).findFirst().orElse(null);
            if (balance == null || CANCELED.equals(balance.getPlanStatus())) continue;
            BigDecimal split = group.stream().filter(p -> p.getBatchNo() > 0).map(SalPaymentPlanDO::getPlanAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = round(o.getTotalAmount().multiply(balance.getPercent()), o.getCurrency()).subtract(split);
            balance.setPlanAmount(target.max(balance.getReceivedAmount()).max(BigDecimal.ZERO));
        }
        BigDecimal total = plans.stream().filter(p -> !CANCELED.equals(p.getPlanStatus())).map(SalPaymentPlanDO::getPlanAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = o.getTotalAmount().subtract(total);
        if (diff.signum() != 0) {
            plans.stream().filter(p -> !CANCELED.equals(p.getPlanStatus()) && p.getPlanAmount().add(diff).compareTo(p.getReceivedAmount()) >= 0)
                    .max(ALLOC_ORDER).ifPresent(p -> p.setPlanAmount(p.getPlanAmount().add(diff)));
        }
        LocalDate today = LocalDate.now();
        for (SalPaymentPlanDO p : plans) {
            if (!CANCELED.equals(p.getPlanStatus())) p.setPlanStatus(status(p, today));
            mapper.updateByIdOrFail(p);
        }
    }

    /** 订单关闭：未出货部分对应的出货类节点余额取消（不再催收） */
    void onClosed(Long orderId) {
        for (SalPaymentPlanDO p : byOrder(orderId)) {
            if (p.getBatchNo() == 0 && SHIPMENT_EVENTS.contains(p.getBaseEvent()) && p.getReceivedAmount().compareTo(p.getPlanAmount()) < 0) {
                p.setPlanStatus(CANCELED);
                mapper.updateByIdOrFail(p);
            }
        }
    }

    /** 订单所有计划已收齐（取消的除外） */
    boolean fullyReceived(Long orderId) {
        return byOrder(orderId).stream().filter(p -> !CANCELED.equals(p.getPlanStatus())).allMatch(p -> RECEIVED.equals(p.getPlanStatus()));
    }

    /** 出货前节点未收齐金额（原币） */
    public BigDecimal unpaidBeforeShipment(Long orderId) {
        return byOrder(orderId).stream().filter(p -> BaseEvent.BEFORE_SHIPMENT.name().equals(p.getBaseEvent()) && !CANCELED.equals(p.getPlanStatus()))
                .map(p -> p.getPlanAmount().subtract(p.getReceivedAmount()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String status(SalPaymentPlanDO p, LocalDate today) {
        if (CANCELED.equals(p.getPlanStatus())) return CANCELED;
        if (p.getReceivedAmount().compareTo(p.getPlanAmount()) >= 0 && p.getPlanAmount().signum() > 0) return RECEIVED;
        if (p.getDueDate() != null && p.getDueDate().isBefore(today)) return OVERDUE;
        if (p.getReceivedAmount().signum() > 0) return PARTIAL;
        if (p.getDueDate() != null && !p.getDueDate().isAfter(today)) return DUE;
        return NOT_DUE;
    }

    /** 每天 00:20 重算状态 */
    @Transactional(rollbackFor = Exception.class)
    public int refreshStatuses() {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (SalPaymentPlanDO p : mapper.selectList(new LambdaQueryWrapper<SalPaymentPlanDO>().in(SalPaymentPlanDO::getPlanStatus, OPEN_STATUSES))) {
            String s = status(p, today);
            if (!s.equals(p.getPlanStatus())) {
                p.setPlanStatus(s);
                mapper.updateByIdOrFail(p);
                n++;
            }
        }
        return n;
    }

    /** 每周一 09:00：逾期计划汇总提醒业务员和其部门负责人（SAL-PP-R03） */
    public int remindOverdue() {
        List<SalPaymentPlanDO> plans = mapper.selectList(new LambdaQueryWrapper<SalPaymentPlanDO>().eq(SalPaymentPlanDO::getPlanStatus, OVERDUE));
        if (plans.isEmpty()) return 0;
        Map<Long, SalOrderDO> orders = orderMapper.selectBatchIds(plans.stream().map(SalPaymentPlanDO::getOrderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, List<SalPaymentPlanDO>> byOwner = new HashMap<>();
        for (SalPaymentPlanDO p : plans) {
            SalOrderDO o = orders.get(p.getOrderId());
            if (o != null) byOwner.computeIfAbsent(o.getOwnerId(), k -> new ArrayList<>()).add(p);
        }
        Map<Long, UserDTO> users = support.users(byOwner.keySet());
        Map<Long, OrgDTO> depts = support.orgs(users.values().stream().map(UserDTO::deptId).toList());
        byOwner.forEach((owner, list) -> {
            UserDTO u = users.get(owner);
            OrgDTO dept = u == null ? null : depts.get(u.deptId());
            List<Long> to = new ArrayList<>();
            to.add(owner);
            if (dept != null && dept.leaderUserId() != null) to.add(dept.leaderUserId());
            Set<String> nos = list.stream().map(p -> orders.get(p.getOrderId()).getDocNo()).collect(Collectors.toCollection(java.util.TreeSet::new));
            support.message(to, "逾期回款提醒", (u == null ? "" : u.realName() + " ") + "有 " + list.size() + " 个回款节点已逾期（订单 "
                    + String.join("、", nos.stream().limit(5).toList()) + (nos.size() > 5 ? " 等" : "") + "），请及时催收", "/sales/payment-plan?overdueOnly=true");
        });
        return byOwner.size();
    }

    // ==================== 查询 ====================

    public PageResult<PaymentPlanRow> page(PaymentPlanQuery q) {
        LambdaQueryWrapper<SalOrderDO> ow = new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false)
                .eq(q.getCustomerId() != null, SalOrderDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, SalOrderDO::getOwnerId, q.getOwnerId())
                .likeRight(StringUtils.hasText(q.getOrderNo()), SalOrderDO::getDocNo, q.getOrderNo() == null ? null : q.getOrderNo().trim().toUpperCase())
                .ne(SalOrderDO::getStatus, DocStatus.VOIDED);
        List<SalOrderDO> orders = orderMapper.selectScopedList(ow.inSql(SalOrderDO::getId, "SELECT order_id FROM sal_payment_plan WHERE deleted = 0"));
        if (orders.isEmpty()) return PageResult.empty();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        LambdaQueryWrapper<SalPaymentPlanDO> w = new LambdaQueryWrapper<SalPaymentPlanDO>().in(SalPaymentPlanDO::getOrderId, byId.keySet())
                .ge(q.getDueFrom() != null, SalPaymentPlanDO::getDueDate, q.getDueFrom())
                .le(q.getDueTo() != null, SalPaymentPlanDO::getDueDate, q.getDueTo());
        if (Boolean.TRUE.equals(q.getOverdueOnly())) w.eq(SalPaymentPlanDO::getPlanStatus, OVERDUE);
        else if (StringUtils.hasText(q.getStatuses())) w.in(SalPaymentPlanDO::getPlanStatus, Arrays.asList(q.getStatuses().split(",")));
        else w.in(SalPaymentPlanDO::getPlanStatus, OPEN_STATUSES);
        w.orderByAsc(SalPaymentPlanDO::getDueDate).orderByAsc(SalPaymentPlanDO::getOrderId).orderByAsc(SalPaymentPlanDO::getSeq).orderByAsc(SalPaymentPlanDO::getBatchNo);
        Page<SalPaymentPlanDO> page = mapper.selectPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(page.getRecords(), byId), page.getTotal());
    }

    List<PaymentPlanRow> rows(List<SalPaymentPlanDO> list, Map<Long, SalOrderDO> orders) {
        Map<Long, CustomerDTO> cs = support.customers(orders.values().stream().map(SalOrderDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(orders.values().stream().map(SalOrderDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        return list.stream().map(p -> {
            SalOrderDO o = orders.get(p.getOrderId());
            int overdue = OVERDUE.equals(p.getPlanStatus()) && p.getDueDate() != null ? (int) ChronoUnit.DAYS.between(p.getDueDate(), today) : 0;
            return new PaymentPlanRow(p.getId(), o.getId(), o.getDocNo(), o.getCustomerId(), SalSupport.shortName(cs, o.getCustomerId()), o.getOwnerId(),
                    SalSupport.name(users, o.getOwnerId()), p.getSeq(), p.getBatchNo(), p.getNodeName(), p.getPercent(), p.getBaseEvent(), p.getDays(),
                    o.getCurrency(), p.getPlanAmount(), p.getEventDate(), p.getDueDate(), overdue, p.getReceivedAmount(),
                    p.getPlanAmount().subtract(p.getReceivedAmount()).max(BigDecimal.ZERO), p.getPlanStatus(), p.getRemark(), p.getPromisedPayDate(),
                    p.getFollowedAt());
        }).toList();
    }

    /** 顶部汇总（本位币，按订单汇率折算；受数据权限约束） */
    public PaymentSummary summary() {
        List<SalOrderDO> orders = orderMapper.selectScopedList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getDeleted, false)
                .ne(SalOrderDO::getStatus, DocStatus.VOIDED).inSql(SalOrderDO::getId, "SELECT order_id FROM sal_payment_plan WHERE deleted = 0"));
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        BigDecimal due = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        if (!orders.isEmpty()) {
            Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
            for (SalPaymentPlanDO p : mapper.selectList(new LambdaQueryWrapper<SalPaymentPlanDO>().in(SalPaymentPlanDO::getOrderId, byId.keySet())
                    .ne(SalPaymentPlanDO::getPlanStatus, CANCELED))) {
                BigDecimal rate = byId.get(p.getOrderId()).getExchangeRate();
                BigDecimal unreceived = p.getPlanAmount().subtract(p.getReceivedAmount()).max(BigDecimal.ZERO).multiply(rate);
                if (p.getDueDate() != null && !p.getDueDate().isBefore(monthStart) && !p.getDueDate().isAfter(monthEnd)) due = due.add(unreceived);
                if (OVERDUE.equals(p.getPlanStatus())) overdue = overdue.add(unreceived);
            }
            // 本月已收：本月回款流水
            received = SalSupport.sum(orderExecReceived(byId, monthStart, monthEnd));
        }
        return new PaymentSummary(due.setScale(2, RoundingMode.HALF_UP), overdue.setScale(2, RoundingMode.HALF_UP),
                received.setScale(2, RoundingMode.HALF_UP), support.baseCurrency());
    }

    /** 回款流水（RECEIPT 执行记录，原币 × 汇率） */
    private List<BigDecimal> orderExecReceived(Map<Long, SalOrderDO> orders, LocalDate from, LocalDate to) {
        return execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, orders.keySet())
                        .eq(SalOrderExecDO::getExecType, OrderExecService.RECEIPT).ge(SalOrderExecDO::getExecDate, from).le(SalOrderExecDO::getExecDate, to))
                .stream().map(e -> SalSupport.nz(e.getAmount()).multiply(orders.get(e.getOrderId()).getExchangeRate())).toList();
    }

    public OrderPaymentSummary orderSummary(SalOrderDO o) {
        List<SalPaymentPlanDO> plans = byOrder(o.getId()).stream().sorted(Comparator.comparing(SalPaymentPlanDO::getSeq)
                .thenComparing(SalPaymentPlanDO::getBatchNo)).toList();
        BigDecimal received = SalSupport.nz(o.getReceivedAmount());
        BigDecimal advance = received.subtract(SalSupport.nz(o.getShippedAmount())).max(BigDecimal.ZERO);
        return new OrderPaymentSummary(o.getTotalAmount(), received, o.getTotalAmount().subtract(received).max(BigDecimal.ZERO), advance,
                rows(plans, Map.of(o.getId(), o)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void followUp(Long id, FollowUpReq req) {
        SalPaymentPlanDO p = mapper.selectById(id);
        if (p == null) throw new BizException(SalesErrorCodes.PAYMENT_PLAN_NOT_EXISTS);
        SalOrderDO o = orderMapper.selectById(p.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "销售订单");
        p.setRemark(SalSupport.trim(req.remark()));
        p.setPromisedPayDate(req.promisedPayDate());
        p.setFollowedAt(LocalDateTime.now());
        mapper.updateByIdOrFail(p);
        support.log(OrderService.BIZ_TYPE, o.getId(), o.getDocNo(), "FOLLOW_UP", "催收", o.getStatus().name(), o.getStatus().name(),
                p.getNodeName() + "：" + Objects.toString(p.getRemark(), "") + (p.getPromisedPayDate() == null ? "" : "（客户承诺 " + p.getPromisedPayDate() + " 付款）"));
    }

    List<SalPaymentPlanDO> byOrder(Long orderId) {
        return new ArrayList<>(mapper.selectList(new LambdaQueryWrapper<SalPaymentPlanDO>().eq(SalPaymentPlanDO::getOrderId, orderId)));
    }

    BigDecimal round(BigDecimal v, String currency) {
        return support.currencyApi().roundAmount(v, currency);
    }
}
