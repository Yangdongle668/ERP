package com.erp.module.pmc.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.api.alert.DeliveryAlertRaisedEvent;
import com.erp.module.pmc.api.mrp.MrpRunCompletedEvent;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.controller.vo.AlertVOs.AlertQuery;
import com.erp.module.pmc.controller.vo.AlertVOs.AlertRow;
import com.erp.module.pmc.controller.vo.AlertVOs.RecalcResult;
import com.erp.module.pmc.controller.vo.AlertVOs.Summary;
import com.erp.module.pmc.dal.dataobject.PmcDeliveryAlertDO;
import com.erp.module.pmc.dal.mapper.PmcDeliveryAlertMapper;
import com.erp.module.pmc.service.AlertStatus;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcAction;
import com.erp.module.pmc.service.PmcStateMachines;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.schedule.ScheduleService;
import com.erp.module.pmc.service.shortage.ShortageService;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.user.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交期预警（需求 06-07）：对未出完的销售订单行估算预计可出货日期（库存 → 生产订单 → 累计提前期），延期时分级预警；延期消除自动关闭。
 */
@Service("pmcDeliveryAlertService")
public class DeliveryAlertService {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryAlertService.class);
    /** 入库检验与出货准备（天） */
    private static final int PREP_DAYS = 1;
    private static final Map<String, Integer> LEVEL_RANK = Map.of("INFO", 1, "WARNING", 2, "CRITICAL", 3);

    private final PmcDeliveryAlertMapper mapper;
    private final PlanningData data;
    private final PmcSupport support;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final ShortageService shortageService;
    private final ScheduleService scheduleService;
    private final DomainEventPublisher eventPublisher;

    public DeliveryAlertService(PmcDeliveryAlertMapper mapper, PlanningData data, PmcSupport support, SalesOrderQueryApi salesOrderQueryApi,
                                ShortageService shortageService, ScheduleService scheduleService, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.data = data;
        this.support = support;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.shortageService = shortageService;
        this.scheduleService = scheduleService;
        this.eventPublisher = eventPublisher;
    }

    /** 一个订单行的估算结果 */
    public record Estimate(SalesOrderLineDTO line, LocalDate due, LocalDate estimated, String cause, String causeDetail) {
        public int delayDays() {
            return (int) ChronoUnit.DAYS.between(due, estimated);
        }
    }

    // ==================== 估算 ====================

    /** 全部未出完订单行的预计可出货日期（需求 06-07 第 2 节） */
    public Map<Long, Estimate> estimate() {
        LocalDate today = LocalDate.now();
        List<SalesOrderLineDTO> lines = salesOrderQueryApi.getOpenLines(OpenLineFilter.all()).stream()
                .filter(l -> l.openQty() != null && l.openQty().signum() > 0 && l.dueDate() != null).toList();
        Map<Long, List<SalesOrderLineDTO>> byMaterial = lines.stream().collect(Collectors.groupingBy(SalesOrderLineDTO::materialId, LinkedHashMap::new,
                Collectors.toList()));
        Set<Long> mids = byMaterial.keySet();
        Map<Long, BigDecimal> avail = new HashMap<>(data.available(mids));
        List<OpenOrderDTO> orders = mids.isEmpty() ? List.of() : data.productionQueryApi().getOpenOrders(mids);
        Map<Long, LocalDate> schedEnd = scheduleService.scheduledEnds();
        Map<Long, ShortageService.OrderResult> shortage = new HashMap<>();
        if (!orders.isEmpty()) {
            List<OpenOrderDTO> all = shortageService.ordered(data.productionQueryApi().getOpenOrders(null).stream()
                    .filter(o -> !"SUSPENDED".equals(o.prodStatus())).toList());
            for (ShortageService.OrderResult r : shortageService.compute(all)) shortage.put(r.order.id(), r);
        }
        Map<Long, MaterialPlanAttr> attrs = support.planAttrs(mids);
        Map<Long, List<OpenOrderDTO>> ordersBy = orders.stream().collect(Collectors.groupingBy(OpenOrderDTO::materialId));
        Map<Long, BigDecimal> orderLeft = new HashMap<>();
        orders.forEach(o -> orderLeft.put(o.id(), o.remainingQty()));
        Map<Long, Integer> cumLead = new HashMap<>();
        Map<Long, Estimate> out = new LinkedHashMap<>();
        for (Map.Entry<Long, List<SalesOrderLineDTO>> e : byMaterial.entrySet()) {
            Long mid = e.getKey();
            List<SalesOrderLineDTO> ls = new ArrayList<>(e.getValue());
            ls.sort(Comparator.comparing(SalesOrderLineDTO::dueDate).thenComparing(SalesOrderLineDTO::orderNo).thenComparingInt(SalesOrderLineDTO::lineNo));
            MaterialPlanAttr pa = attrs.get(mid);
            int lead = pa == null ? 0 : pa.leadTimeDays();
            for (SalesOrderLineDTO l : ls) {
                BigDecimal need = l.openQty();
                BigDecimal have = avail.getOrDefault(mid, BigDecimal.ZERO);
                BigDecimal take = PmcSupport.min(have, need);
                avail.put(mid, have.subtract(take));
                need = need.subtract(take);
                LocalDate est = today;
                String cause = null;
                String detail = null;
                if (need.signum() > 0) {
                    List<OpenOrderDTO> cand = new ArrayList<>(ordersBy.getOrDefault(mid, List.of()));
                    cand.sort(Comparator.comparing((OpenOrderDTO o) -> l.lineId().equals(o.salesOrderLineId()) ? 0 : 1).thenComparing(OpenOrderDTO::planEnd));
                    for (OpenOrderDTO o : cand) {
                        if (need.signum() <= 0) break;
                        if (o.salesOrderLineId() != null && !o.salesOrderLineId().equals(l.lineId())) continue;
                        BigDecimal left = orderLeft.getOrDefault(o.id(), BigDecimal.ZERO);
                        if (left.signum() <= 0) continue;
                        BigDecimal t = PmcSupport.min(left, need);
                        orderLeft.put(o.id(), left.subtract(t));
                        need = need.subtract(t);
                        LocalDate finish = schedEnd.getOrDefault(o.id(), o.planEnd());
                        String c = finish.isAfter(o.planEnd()) ? "CAPACITY" : "WO_DELAY";
                        String d = o.docNo() + (finish.isAfter(o.planEnd()) ? " 排产完工 " + finish : " 计划完工 " + finish);
                        ShortageService.OrderResult sr = shortage.get(o.id());
                        if (sr != null && sr.short_()) {
                            LocalDate kit = sr.eta != null ? sr.eta : today.plusDays(cumulative(o.materialId(), cumLead));
                            if (kit.isAfter(o.planStart())) {
                                LocalDate f2 = kit.plusDays(lead);
                                if (f2.isAfter(finish)) {
                                    finish = f2;
                                    c = "MATERIAL_SHORTAGE";
                                    d = shortText(sr, o) + (sr.eta != null ? "，预计 " + sr.eta + " 齐套" : "，无供应");
                                }
                            }
                        }
                        LocalDate x = finish.plusDays(PREP_DAYS);
                        if (!x.isBefore(est)) {
                            est = x;
                            cause = c;
                            detail = d;
                        }
                    }
                    if (need.signum() > 0) {
                        LocalDate x = today.plusDays(cumulative(mid, cumLead));
                        if (!x.isBefore(est)) {
                            est = x;
                            cause = "NO_STOCK_NO_WO";
                            detail = "无库存、无生产订单覆盖 " + PmcSupport.plain(need) + "，按累计提前期估算";
                        }
                    }
                }
                out.put(l.lineId(), new Estimate(l, l.dueDate(), est, cause == null ? "WO_DELAY" : cause, detail));
            }
        }
        return out;
    }

    private String shortText(ShortageService.OrderResult r, OpenOrderDTO o) {
        Map<Long, MaterialDTO> ms = support.materials(r.lines.stream().map(l -> l.componentId).toList());
        String s = r.lines.stream().filter(l -> l.shortage.signum() > 0).limit(3)
                .map(l -> PmcSupport.code(ms, l.componentId) + " 缺 " + PmcSupport.plain(l.shortage)).collect(Collectors.joining("、"));
        return o.docNo() + " 缺料：" + s;
    }

    /** 累计提前期 = 最长子件采购提前期 + 生产提前期（单层，自制半成品取其生产提前期） */
    private int cumulative(Long materialId, Map<Long, Integer> cache) {
        Integer v = cache.get(materialId);
        if (v != null) return v;
        MaterialPlanAttr pa = support.materialApi().getPlanAttr(materialId);
        int lead = pa == null ? 0 : pa.leadTimeDays();
        int comp = 0;
        BomDTO bom = data.bomApi().getDefaultBom(materialId, LocalDate.now()).orElse(null);
        if (bom != null) {
            for (BomDTO.Line l : bom.lines()) {
                MaterialPlanAttr ca = support.materialApi().getPlanAttr(l.componentId());
                MaterialPurchaseAttr cp = support.materialApi().getPurchaseAttr(l.componentId());
                int d = ca != null && ca.leadTimeDays() > 0 ? ca.leadTimeDays() : cp == null ? 0 : cp.leadTimeDays();
                comp = Math.max(comp, d);
            }
        }
        cache.put(materialId, lead + comp);
        return lead + comp;
    }

    private String level(int delay) {
        String cfg = support.params().getString(PmcModuleConfig.P_ALERT_LEVELS);
        int info = 2;
        int warn = 7;
        try {
            String[] p = (cfg == null ? "2,7" : cfg).split(",");
            info = Integer.parseInt(p[0].trim());
            warn = Integer.parseInt(p[1].trim());
        } catch (RuntimeException ignored) {
            // 参数格式错误时按默认阈值
        }
        return delay <= info ? "INFO" : delay <= warn ? "WARNING" : "CRITICAL";
    }

    // ==================== 计算与维护 ====================

    @ErpJob(code = "PMC_DELIVERY_ALERT", name = "交期预警计算", cron = "0 0 6 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String job() {
        RecalcResult r = recalculate();
        return "订单行 " + r.lineCount() + "，预警 " + r.alertCount() + "，新增/升级 " + r.raised() + "，消除 " + r.resolved();
    }

    /** MRP 运算成功后重新计算 */
    @EventListener
    public void onMrp(MrpRunCompletedEvent e) {
        if (!"SUCCESS".equals(e.getRunStatus())) return;
        try {
            recalculate();
        } catch (RuntimeException ex) {
            LOG.warn("交期预警计算失败：{}", ex.getMessage(), ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RecalcResult recalculate() {
        Map<Long, Estimate> est = estimate();
        Map<Long, PmcDeliveryAlertDO> existing = mapper.selectList(new LambdaQueryWrapper<PmcDeliveryAlertDO>()).stream()
                .collect(Collectors.toMap(PmcDeliveryAlertDO::getOrderLineId, a -> a, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        int alerts = 0;
        int raised = 0;
        int resolved = 0;
        for (Estimate e : est.values()) {
            PmcDeliveryAlertDO a = existing.remove(e.line().lineId());
            int delay = e.delayDays();
            if (delay <= 0) {
                if (a != null && !AlertStatus.CLOSED.name().equals(a.getHandleStatus())) {
                    resolve(a, now);
                    resolved++;
                }
                continue;
            }
            alerts++;
            String lvl = level(delay);
            boolean isNew = a == null || AlertStatus.CLOSED.name().equals(a.getHandleStatus());
            boolean upgraded = !isNew && LEVEL_RANK.get(lvl) > LEVEL_RANK.getOrDefault(a.getAlertLevel(), 0);
            if (a == null) {
                a = new PmcDeliveryAlertDO();
                a.setOrderLineId(e.line().lineId());
                a.setHandleStatus(AlertStatus.OPEN.name());
            } else if (AlertStatus.CLOSED.name().equals(a.getHandleStatus())) {
                a.setHandleStatus(PmcStateMachines.ALERT.fire(AlertStatus.CLOSED, PmcAction.REOPEN).name());
            } else if (AlertStatus.HANDLED.name().equals(a.getHandleStatus()) && a.getHandledDelayDays() != null
                    && delay > a.getHandledDelayDays() + 3) {
                // R02：已处理的预警延期继续增加超过 3 天，重新变为未处理
                a.setHandleStatus(PmcStateMachines.ALERT.fire(AlertStatus.HANDLED, PmcAction.REOPEN).name());
                upgraded = true;
            }
            SalesOrderLineDTO l = e.line();
            a.setOrderId(l.orderId());
            a.setOrderNo(l.orderNo());
            a.setOrderLineNo(l.lineNo());
            a.setCustomerId(l.customerId());
            a.setSalesOwnerId(l.ownerId());
            a.setSalesDeptId(l.deptId());
            a.setMaterialId(l.materialId());
            a.setOpenQty(l.openQty());
            a.setPromisedDate(e.due());
            a.setEstimatedDate(e.estimated());
            a.setDelayDays(delay);
            a.setAlertLevel(lvl);
            a.setCause(e.cause());
            a.setCauseDetail(e.causeDetail() == null ? null : e.causeDetail().length() > 512 ? e.causeDetail().substring(0, 512) : e.causeDetail());
            a.setCalculatedAt(now);
            if (a.getId() == null) mapper.insert(a);
            else mapper.updateByIdOrFail(a);
            if (isNew || upgraded) {
                raise(a);
                raised++;
            }
        }
        // 已出完 / 已关闭的订单行：预警消除
        for (PmcDeliveryAlertDO a : existing.values()) {
            if (!AlertStatus.CLOSED.name().equals(a.getHandleStatus())) {
                resolve(a, now);
                resolved++;
            }
        }
        return new RecalcResult(est.size(), alerts, raised, resolved);
    }

    private void resolve(PmcDeliveryAlertDO a, LocalDateTime now) {
        a.setHandleStatus(PmcStateMachines.ALERT.fire(AlertStatus.valueOf(a.getHandleStatus()), PmcAction.RESOLVE).name());
        a.setDelayDays(0);
        a.setCalculatedAt(now);
        mapper.updateByIdOrFail(a);
        support.notifyApi().resolve("PMC_DELIVERY:" + a.getOrderLineId());
    }

    /** R01：工作台预警给计划员和业务员；严重预警同时通知 PMC 主管 */
    private void raise(PmcDeliveryAlertDO a) {
        MaterialPlanAttr pa = support.materialApi().getPlanAttr(a.getMaterialId());
        List<Long> users = new ArrayList<>();
        if (pa != null && pa.plannerId() != null) users.add(pa.plannerId());
        if (a.getSalesOwnerId() != null) users.add(a.getSalesOwnerId());
        if ("CRITICAL".equals(a.getAlertLevel())) {
            List<Long> managers = support.params().getUserIds(PmcModuleConfig.P_ALERT_MANAGERS);
            users.addAll(managers == null || managers.isEmpty() ? support.usersWithPermission("pmc:alert:handle") : managers);
        }
        MaterialDTO m = support.materials(List.of(a.getMaterialId())).get(a.getMaterialId());
        String title = "交期预警：" + a.getOrderNo() + " 行 " + a.getOrderLineNo() + " 预计延期 " + a.getDelayDays() + " 天";
        String content = (m == null ? "" : m.code() + " " + m.name() + "，") + "承诺 " + a.getPromisedDate() + "，预计 " + a.getEstimatedDate()
                + (a.getCauseDetail() == null ? "" : "；" + a.getCauseDetail());
        support.notifyApi().alert(new AlertRaisedEvent("PMC_DELIVERY:" + a.getOrderLineId(), "PMC_DELIVERY",
                AlertRaisedEvent.Level.valueOf(a.getAlertLevel()), users.stream().distinct().toList(), null, "PMC_DELIVERY_ALERT", a.getId(), title, content,
                "/pmc/alert"));
        eventPublisher.publish(new DeliveryAlertRaisedEvent(a.getId(), a.getOrderLineId(), a.getOrderNo(), a.getCustomerId(), a.getMaterialId(),
                a.getSalesOwnerId(), a.getPromisedDate(), a.getEstimatedDate(), a.getDelayDays(), a.getAlertLevel(), a.getCause(), a.getCauseDetail()));
    }

    // ==================== 处理、查询 ====================

    private PmcDeliveryAlertDO getOrThrow(Long id) {
        PmcDeliveryAlertDO a = id == null ? null : mapper.selectById(id);
        if (a == null) throw new BizException(PmcErrorCodes.ALERT_NOT_EXISTS);
        return a;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String remark) {
        PmcDeliveryAlertDO a = getOrThrow(id);
        String r = PmcSupport.requireReason(remark, "处理说明");
        a.setHandleStatus(PmcStateMachines.ALERT.fire(AlertStatus.valueOf(a.getHandleStatus()), PmcAction.HANDLE).name());
        a.setHandleRemark(r);
        a.setHandledDelayDays(a.getDelayDays());
        a.setHandledBy(support.currentUser());
        a.setHandledAt(LocalDateTime.now());
        mapper.updateByIdOrFail(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id, String reason) {
        PmcDeliveryAlertDO a = getOrThrow(id);
        String r = PmcSupport.requireReason(reason, "忽略");
        a.setHandleStatus(PmcStateMachines.ALERT.fire(AlertStatus.valueOf(a.getHandleStatus()), PmcAction.IGNORE).name());
        a.setHandleRemark(r);
        a.setHandledDelayDays(a.getDelayDays());
        a.setHandledBy(support.currentUser());
        a.setHandledAt(LocalDateTime.now());
        mapper.updateByIdOrFail(a);
    }

    private LambdaQueryWrapper<PmcDeliveryAlertDO> where(AlertQuery q, boolean withStatus) {
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : List.of(AlertStatus.OPEN.name());
        return new LambdaQueryWrapper<PmcDeliveryAlertDO>().eq(PmcDeliveryAlertDO::getDeleted, false)
                .ne(PmcDeliveryAlertDO::getHandleStatus, AlertStatus.CLOSED.name())
                .in(withStatus, PmcDeliveryAlertDO::getHandleStatus, statuses)
                .in(StringUtils.hasText(q.getLevels()), PmcDeliveryAlertDO::getAlertLevel, Arrays.asList(String.valueOf(q.getLevels()).split(",")))
                .eq(StringUtils.hasText(q.getCause()), PmcDeliveryAlertDO::getCause, q.getCause())
                .eq(q.getCustomerId() != null, PmcDeliveryAlertDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, PmcDeliveryAlertDO::getSalesOwnerId, q.getOwnerId())
                .eq(q.getMaterialId() != null, PmcDeliveryAlertDO::getMaterialId, q.getMaterialId());
    }

    public PageResult<AlertRow> page(AlertQuery q) {
        LambdaQueryWrapper<PmcDeliveryAlertDO> w = where(q, true).orderByDesc(PmcDeliveryAlertDO::getDelayDays).orderByAsc(PmcDeliveryAlertDO::getPromisedDate);
        IPage<PmcDeliveryAlertDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        List<PmcDeliveryAlertDO> list = p.getRecords();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcDeliveryAlertDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = data.customers(list.stream().map(PmcDeliveryAlertDO::getCustomerId).toList());
        Set<Long> uids = new HashSet<>();
        list.forEach(a -> {
            uids.add(a.getSalesOwnerId());
            uids.add(a.getHandledBy());
        });
        Map<Long, UserDTO> users = support.users(uids);
        return new PageResult<>(list.stream().map(a -> {
            MaterialDTO m = ms.get(a.getMaterialId());
            return new AlertRow(a.getId(), a.getOrderId(), a.getOrderNo(), a.getOrderLineNo(), a.getOrderLineId(), a.getCustomerId(),
                    PlanningData.customerName(cs, a.getCustomerId()), a.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    a.getOpenQty(), a.getPromisedDate(), a.getEstimatedDate(), a.getDelayDays(), a.getAlertLevel(), a.getCause(), a.getCauseDetail(),
                    a.getSalesOwnerId(), PmcSupport.name(users, a.getSalesOwnerId()), a.getHandleStatus(), a.getHandleRemark(),
                    PmcSupport.name(users, a.getHandledBy()), a.getHandledAt(), a.getCalculatedAt());
        }).toList(), p.getTotal());
    }

    /** 顶部卡片：未处理的严重、警告、提示数量 */
    public Summary summary(AlertQuery q) {
        List<PmcDeliveryAlertDO> list = mapper.selectScopedList(where(q, true));
        return new Summary(list.stream().filter(a -> "CRITICAL".equals(a.getAlertLevel())).count(),
                list.stream().filter(a -> "WARNING".equals(a.getAlertLevel())).count(), list.stream().filter(a -> "INFO".equals(a.getAlertLevel())).count());
    }

    /** 预计可出货日期：有未关闭的预警取预警结果，否则实时估算 */
    public Optional<LocalDate> estimatedDate(Long orderLineId) {
        PmcDeliveryAlertDO a = mapper.selectOne(new LambdaQueryWrapper<PmcDeliveryAlertDO>().eq(PmcDeliveryAlertDO::getOrderLineId, orderLineId));
        if (a != null && !AlertStatus.CLOSED.name().equals(a.getHandleStatus())) return Optional.of(a.getEstimatedDate());
        Estimate e = estimate().get(orderLineId);
        return e == null ? Optional.empty() : Optional.of(e.estimated());
    }
}
