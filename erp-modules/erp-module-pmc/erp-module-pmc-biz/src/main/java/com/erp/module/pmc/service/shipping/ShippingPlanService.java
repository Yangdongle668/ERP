package com.erp.module.pmc.service.shipping;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.api.shipping.ShippingPlanApi;
import com.erp.module.pmc.api.shipping.ShippingPlanLineDTO;
import com.erp.module.pmc.api.shipping.ShippingPlanPublishedEvent;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.controller.vo.ShippingVOs.LineSave;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanDetail;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanLine;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanQuery;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanRow;
import com.erp.module.pmc.controller.vo.ShippingVOs.PlanSave;
import com.erp.module.pmc.dal.dataobject.PmcShippingPlanDO;
import com.erp.module.pmc.dal.dataobject.PmcShippingPlanLineDO;
import com.erp.module.pmc.dal.mapper.PmcShippingPlanLineMapper;
import com.erp.module.pmc.dal.mapper.PmcShippingPlanMapper;
import com.erp.module.pmc.service.PlanStatus;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcAction;
import com.erp.module.pmc.service.PmcStateMachines;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.Weeks;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出货计划（需求 06-08）：按周从到期的销售订单行生成计划（成品可用库存按交期先后分配），发布后出货模块据此生成出货通知；
 * 出货通知回写已通知数量，发布后仍可修改未通知的行。
 */
@Service("pmcShippingPlanService")
public class ShippingPlanService implements ShippingPlanApi {

    public static final String PLANNED = "PLANNED";
    public static final String NOTICED = "NOTICED";
    public static final String CANCELED = "CANCELED";

    private final PmcShippingPlanMapper mapper;
    private final PmcShippingPlanLineMapper lineMapper;
    private final PmcSupport support;
    private final PlanningData data;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final DomainEventPublisher eventPublisher;

    public ShippingPlanService(PmcShippingPlanMapper mapper, PmcShippingPlanLineMapper lineMapper, PmcSupport support, PlanningData data,
                               SalesOrderQueryApi salesOrderQueryApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.support = support;
        this.data = data;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<PlanRow> page(PlanQuery q) {
        LambdaQueryWrapper<PmcShippingPlanDO> w = new LambdaQueryWrapper<PmcShippingPlanDO>().eq(PmcShippingPlanDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), PmcShippingPlanDO::getDocNo, q.getDocNo())
                .eq(StringUtils.hasText(q.getWeek()), PmcShippingPlanDO::getPlanWeek, q.getWeek())
                .in(StringUtils.hasText(q.getStatuses()), PmcShippingPlanDO::getPlanStatus, Arrays.asList(String.valueOf(q.getStatuses()).split(",")))
                .orderByDesc(PmcShippingPlanDO::getPlanWeek).orderByDesc(PmcShippingPlanDO::getId);
        IPage<PmcShippingPlanDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        Map<Long, List<PmcShippingPlanLineDO>> lines = p.getRecords().isEmpty() ? Map.of()
                : lineMapper.selectByParents(p.getRecords().stream().map(PmcShippingPlanDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(PmcShippingPlanLineDO::getPlanId));
        Map<Long, UserDTO> users = support.users(p.getRecords().stream().map(PmcShippingPlanDO::getOwnerId).toList());
        return new PageResult<>(p.getRecords().stream().map(s -> {
            List<PmcShippingPlanLineDO> ls = lines.getOrDefault(s.getId(), List.of()).stream().filter(l -> !CANCELED.equals(l.getLineStatus())).toList();
            return new PlanRow(s.getId(), s.getDocNo(), s.getPlanWeek(), s.getPlanStatus(), ls.size(), PmcSupport.sum(ls.stream().map(PmcShippingPlanLineDO::getPlanQty).toList()),
                    (int) ls.stream().filter(l -> NOTICED.equals(l.getLineStatus())).count(), s.getPublishedAt(), PmcSupport.name(users, s.getOwnerId()),
                    s.getCreatedAt());
        }).toList(), p.getTotal());
    }

    public PmcShippingPlanDO getOrThrow(Long id) {
        PmcShippingPlanDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw new BizException(PmcErrorCodes.SHIP_PLAN_NOT_EXISTS);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "出货计划");
        return s;
    }

    public PlanDetail detail(Long id) {
        PmcShippingPlanDO s = getOrThrow(id);
        List<PmcShippingPlanLineDO> list = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcShippingPlanLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = data.customers(list.stream().map(PmcShippingPlanLineDO::getCustomerId).toList());
        List<PlanLine> lines = list.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new PlanLine(l.getId(), l.getLineNo(), l.getOrderLineId(), l.getOrderId(), l.getOrderNo(), l.getOrderLineNo(), l.getCustomerId(),
                    PlanningData.customerName(cs, l.getCustomerId()), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.baseUom(), l.getDueDate(), l.getOpenQty(), l.getAvailableQty(), l.getPlanQty(), l.getPlanShipDate(),
                    l.getTransportMode(), l.getNoticedQty(), l.getLineStatus(), l.getRemark(), l.getPlanQty().compareTo(l.getOpenQty()) < 0);
        }).toList();
        return new PlanDetail(s.getId(), s.getDocNo(), s.getPlanWeek(), s.getPlanStatus(), s.getRemark(), s.getPublishedAt(),
                support.userName(s.getOwnerId()), s.getCreatedAt(), s.getVersion() == null ? 0 : s.getVersion(), lines);
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(PlanSave req) {
        Weeks.monday(req.planWeek());
        PmcShippingPlanDO s = new PmcShippingPlanDO();
        s.setDocNo(support.nextNo(PmcModuleConfig.SHIPPING_PLAN));
        s.setDocDate(LocalDate.now());
        s.setPlanWeek(req.planWeek().trim());
        s.setPlanStatus(PlanStatus.DRAFT.name());
        s.setStatus(PlanStatus.DRAFT.docStatus());
        s.setRemark(PmcSupport.trim(req.remark()));
        support.fillOwner(s);
        mapper.insert(s);
        saveLines(s, req.lines());
        support.log(PmcModuleConfig.SHIPPING_PLAN, s.getId(), s.getDocNo(), "CREATE", "新建", null, PlanStatus.DRAFT.name(), null);
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PlanSave req) {
        PmcShippingPlanDO s = getOrThrow(id);
        if (PlanStatus.CLOSED.name().equals(s.getPlanStatus())) throw new BizException(PmcErrorCodes.SHIP_PLAN_NOT_EDITABLE);
        if (req.version() != null) s.setVersion(req.version());
        if (PlanStatus.DRAFT.name().equals(s.getPlanStatus())) {
            Weeks.monday(req.planWeek());
            s.setPlanWeek(req.planWeek().trim());
        }
        s.setRemark(PmcSupport.trim(req.remark()));
        mapper.updateByIdOrFail(s);
        saveLines(s, req.lines());
    }

    /** 明细整体保存：R01 计划 ≤ 未出货；R02 同一订单行同一周只能一条；R03 已通知的行不能删除或减少到已通知数量以下 */
    private void saveLines(PmcShippingPlanDO s, List<LineSave> lines) {
        List<LineSave> ls = lines == null ? List.of() : lines;
        Map<Long, PmcShippingPlanLineDO> existing = lineMapper.selectByParent(s.getId()).stream()
                .collect(Collectors.toMap(PmcShippingPlanLineDO::getId, l -> l));
        Map<Long, SalesOrderLineDTO> sales = salesOrderQueryApi.getLines(ls.stream().map(LineSave::orderLineId).collect(Collectors.toSet()));
        Set<Long> seen = new HashSet<>();
        Set<Long> others = otherPlanLines(s);
        LocalDate monday = Weeks.monday(s.getPlanWeek());
        Set<Long> kept = new HashSet<>();
        int no = 0;
        for (LineSave r : ls) {
            SalesOrderLineDTO sl = sales.get(r.orderLineId());
            if (sl == null) throw new BizException(PmcErrorCodes.SHIP_PLAN_ORDER_LINE_INVALID);
            if (!seen.add(r.orderLineId()) || others.contains(r.orderLineId())) {
                throw BizException.of(PmcErrorCodes.SHIP_PLAN_LINE_DUPLICATED, sl.orderNo(), sl.lineNo());
            }
            PmcShippingPlanLineDO l = r.id() == null ? null : existing.get(r.id());
            BigDecimal noticed = l == null ? BigDecimal.ZERO : l.getNoticedQty();
            BigDecimal open = PmcSupport.max0(sl.openQty());
            BigDecimal qty = PmcSupport.max0(r.planQty());
            if (qty.compareTo(open) > 0) throw BizException.of(PmcErrorCodes.SHIP_PLAN_QTY_EXCEEDED, PmcSupport.plain(open));
            if (qty.compareTo(noticed) < 0) throw BizException.of(PmcErrorCodes.SHIP_PLAN_LINE_NOTICED, sl.orderNo(), sl.lineNo());
            if (l == null) {
                l = new PmcShippingPlanLineDO();
                l.setPlanId(s.getId());
                l.setOrderLineId(sl.lineId());
                l.setNoticedQty(BigDecimal.ZERO);
                l.setAvailableQty(r.planQty());
            }
            l.setLineNo(++no);
            l.setOrderId(sl.orderId());
            l.setOrderNo(sl.orderNo());
            l.setOrderLineNo(sl.lineNo());
            l.setCustomerId(sl.customerId());
            l.setMaterialId(sl.materialId());
            l.setDueDate(sl.dueDate());
            l.setOpenQty(open);
            l.setPlanQty(qty);
            LocalDate date = r.planShipDate() != null ? r.planShipDate() : sl.dueDate() == null || sl.dueDate().isBefore(monday) ? monday : sl.dueDate();
            l.setPlanShipDate(date);
            l.setTransportMode(PmcSupport.trim(r.transportMode()));
            l.setRemark(PmcSupport.trim(r.remark()));
            l.setLineStatus(noticed.signum() > 0 && noticed.compareTo(qty) >= 0 ? NOTICED : PLANNED);
            if (l.getId() == null) lineMapper.insert(l);
            else lineMapper.updateByIdOrFail(l);
            kept.add(l.getId());
        }
        for (PmcShippingPlanLineDO l : existing.values()) {
            if (kept.contains(l.getId())) continue;
            if (l.getNoticedQty().signum() > 0) throw BizException.of(PmcErrorCodes.SHIP_PLAN_LINE_NOTICED, l.getOrderNo(), l.getOrderLineNo());
            lineMapper.deleteById(l.getId());
        }
    }

    /** 同一周其他（未关闭）出货计划中的订单行 */
    private Set<Long> otherPlanLines(PmcShippingPlanDO s) {
        List<Long> ids = mapper.selectList(new LambdaQueryWrapper<PmcShippingPlanDO>().eq(PmcShippingPlanDO::getPlanWeek, s.getPlanWeek())
                .ne(PmcShippingPlanDO::getId, s.getId()).ne(PmcShippingPlanDO::getPlanStatus, PlanStatus.CLOSED.name())).stream()
                .map(PmcShippingPlanDO::getId).toList();
        if (ids.isEmpty()) return Set.of();
        return lineMapper.selectByParents(ids).stream().filter(l -> !CANCELED.equals(l.getLineStatus())).map(PmcShippingPlanLineDO::getOrderLineId)
                .collect(Collectors.toSet());
    }

    /**
     * 生成计划（草稿）：承诺交期（无则要求交期）在该周及之前、未出货 > 0 的订单行；计划 = min(未出货, 可用库存按交期先后分配)，
     * 计划日期 = max(交期, 周一)；运输方式默认该客户上一次的。已在明细中的行保留。
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanDetail generate(Long id) {
        PmcShippingPlanDO s = getOrThrow(id);
        if (!PlanStatus.DRAFT.name().equals(s.getPlanStatus())) throw BizException.of(PmcErrorCodes.STATUS_NOT_ALLOWED, "已发布", "生成");
        LocalDate monday = Weeks.monday(s.getPlanWeek());
        LocalDate sunday = Weeks.sunday(s.getPlanWeek());
        List<SalesOrderLineDTO> lines = salesOrderQueryApi.getOpenLines(new OpenLineFilter(null, null, null, null, sunday)).stream()
                .filter(l -> l.openQty() != null && l.openQty().signum() > 0).toList();
        List<PmcShippingPlanLineDO> existing = lineMapper.selectByParent(id);
        Set<Long> have = existing.stream().map(PmcShippingPlanLineDO::getOrderLineId).collect(Collectors.toSet());
        have.addAll(otherPlanLines(s));
        Map<Long, BigDecimal> avail = new HashMap<>(data.available(lines.stream().map(SalesOrderLineDTO::materialId).collect(Collectors.toSet())));
        // 已在本计划中的行先占用可用
        existing.forEach(l -> avail.merge(l.getMaterialId(), l.getPlanQty().subtract(l.getNoticedQty()).negate(), BigDecimal::add));
        Map<Long, String> lastMode = new HashMap<>();
        int no = existing.size();
        List<SalesOrderLineDTO> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(SalesOrderLineDTO::dueDate).thenComparing(SalesOrderLineDTO::orderNo).thenComparingInt(SalesOrderLineDTO::lineNo));
        for (SalesOrderLineDTO l : sorted) {
            if (have.contains(l.lineId())) continue;
            BigDecimal a = PmcSupport.max0(avail.getOrDefault(l.materialId(), BigDecimal.ZERO));
            BigDecimal qty = PmcSupport.min(a, l.openQty());
            avail.put(l.materialId(), a.subtract(qty));
            PmcShippingPlanLineDO d = new PmcShippingPlanLineDO();
            d.setPlanId(id);
            d.setLineNo(++no);
            d.setOrderLineId(l.lineId());
            d.setOrderId(l.orderId());
            d.setOrderNo(l.orderNo());
            d.setOrderLineNo(l.lineNo());
            d.setCustomerId(l.customerId());
            d.setMaterialId(l.materialId());
            d.setDueDate(l.dueDate());
            d.setOpenQty(l.openQty());
            d.setAvailableQty(a);
            d.setPlanQty(qty);
            d.setPlanShipDate(l.dueDate().isBefore(monday) ? monday : l.dueDate());
            d.setTransportMode(lastMode.computeIfAbsent(l.customerId(), this::lastTransportMode));
            d.setNoticedQty(BigDecimal.ZERO);
            d.setLineStatus(PLANNED);
            lineMapper.insert(d);
        }
        return detail(id);
    }

    private String lastTransportMode(Long customerId) {
        PmcShippingPlanLineDO l = lineMapper.selectOne(new LambdaQueryWrapper<PmcShippingPlanLineDO>().eq(PmcShippingPlanLineDO::getCustomerId, customerId)
                .isNotNull(PmcShippingPlanLineDO::getTransportMode).orderByDesc(PmcShippingPlanLineDO::getId).last("LIMIT 1"));
        return l == null ? null : l.getTransportMode();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmcShippingPlanDO s = getOrThrow(id);
        if (!PlanStatus.DRAFT.name().equals(s.getPlanStatus())) throw BizException.of(PmcErrorCodes.STATUS_NOT_ALLOWED, PlanStatus.valueOf(s.getPlanStatus()).label(), "删除");
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    private void fire(PmcShippingPlanDO s, PmcAction action) {
        PlanStatus from = PlanStatus.valueOf(s.getPlanStatus());
        PlanStatus to = PmcStateMachines.PLAN.fire(from, action);
        s.setPlanStatus(to.name());
        s.setStatus(to.docStatus());
        mapper.updateByIdOrFail(s);
        support.log(PmcModuleConfig.SHIPPING_PLAN, s.getId(), s.getDocNo(), action.name(), action.label(), from.name(), to.name(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        PmcShippingPlanDO s = getOrThrow(id);
        s.setPublishedAt(LocalDateTime.now());
        fire(s, PmcAction.PUBLISH);
        List<Long> lines = lineMapper.selectByParent(id).stream().filter(l -> !CANCELED.equals(l.getLineStatus()))
                .map(PmcShippingPlanLineDO::getOrderLineId).toList();
        eventPublisher.publish(new ShippingPlanPublishedEvent(s.getId(), s.getDocNo(), s.getPlanWeek(), lines));
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        fire(getOrThrow(id), PmcAction.CLOSE);
    }

    // ==================== ShippingPlanApi ====================

    @Override
    public List<ShippingPlanLineDTO> getPlanLines(String week) {
        List<PmcShippingPlanDO> plans = mapper.selectList(new LambdaQueryWrapper<PmcShippingPlanDO>().eq(PmcShippingPlanDO::getPlanWeek, week)
                .eq(PmcShippingPlanDO::getPlanStatus, PlanStatus.PUBLISHED.name()));
        if (plans.isEmpty()) return List.of();
        Map<Long, PmcShippingPlanDO> byId = plans.stream().collect(Collectors.toMap(PmcShippingPlanDO::getId, p -> p));
        return lineMapper.selectByParents(byId.keySet()).stream().filter(l -> !CANCELED.equals(l.getLineStatus())).map(l -> {
            PmcShippingPlanDO p = byId.get(l.getPlanId());
            return new ShippingPlanLineDTO(l.getId(), p.getId(), p.getDocNo(), p.getPlanWeek(), l.getOrderLineId(), l.getCustomerId(), l.getMaterialId(),
                    l.getPlanQty(), l.getNoticedQty(), l.getPlanShipDate(), l.getTransportMode(), l.getLineStatus(), l.getRemark());
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onNoticed(Long planLineId, BigDecimal deltaQty) {
        PmcShippingPlanLineDO l = lineMapper.selectById(planLineId);
        if (l == null) throw new BizException(PmcErrorCodes.SHIP_PLAN_NOT_EXISTS);
        l.setNoticedQty(PmcSupport.max0(l.getNoticedQty().add(PmcSupport.nz(deltaQty))));
        l.setLineStatus(l.getNoticedQty().signum() > 0 && l.getNoticedQty().compareTo(l.getPlanQty()) >= 0 ? NOTICED : PLANNED);
        lineMapper.updateByIdOrFail(l);
    }
}
