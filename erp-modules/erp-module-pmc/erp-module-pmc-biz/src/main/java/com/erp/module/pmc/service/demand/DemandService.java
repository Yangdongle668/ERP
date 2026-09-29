package com.erp.module.pmc.service.demand;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandQuery;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandRow;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandSave;
import com.erp.module.pmc.dal.dataobject.PmcDemandDO;
import com.erp.module.pmc.dal.mapper.PmcDemandMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.purchase.api.order.InTransitDTO;
import com.erp.module.sales.api.forecast.ForecastApi;
import com.erp.module.sales.api.forecast.ForecastPublishedEvent;
import com.erp.module.sales.api.forecast.NetForecastDTO;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderApprovedEvent;
import com.erp.module.sales.api.order.SalesOrderChangedEvent;
import com.erp.module.sales.api.order.SalesOrderClosedEvent;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderPromisedDateChangedEvent;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.order.SalesOrderShipmentChangedEvent;
import com.erp.module.sales.api.order.SalesOrderUnapprovedEvent;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.user.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 需求池（需求 06-01 第 3 节）：销售订单、净预测由事件自动维护，每天 01:30 全量对账；计划员可补充手工需求。
 * 安全库存在 MRP 运算时临时计算，不落需求池。
 */
@Service("pmcDemandService")
public class DemandService {

    private static final Logger LOG = LoggerFactory.getLogger(DemandService.class);

    public static final String SALES_ORDER = "SALES_ORDER";
    public static final String FORECAST = "FORECAST";
    public static final String MANUAL = "MANUAL";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String REPLY_PENDING = "PENDING";
    public static final String REPLY_DONE = "REPLIED";
    public static final String REPLY_AGAIN = "REREPLY";
    private static final Set<String> ACTIVE_ORDER = Set.of("APPROVED", "IN_PROGRESS");

    private final PmcDemandMapper mapper;
    private final PmcSupport support;
    private final PlanningData data;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final ForecastApi forecastApi;

    public DemandService(PmcDemandMapper mapper, PmcSupport support, PlanningData data, SalesOrderQueryApi salesOrderQueryApi, ForecastApi forecastApi) {
        this.mapper = mapper;
        this.support = support;
        this.data = data;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.forecastApi = forecastApi;
    }

    // ==================== 事件维护 ====================

    @EventListener
    public void onApproved(SalesOrderApprovedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onChanged(SalesOrderChangedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onClosed(SalesOrderClosedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onUnapproved(SalesOrderUnapprovedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onShipment(SalesOrderShipmentChangedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onPromised(SalesOrderPromisedDateChangedEvent e) {
        safe(() -> syncOrder(e.getOrderId()));
    }

    @EventListener
    public void onForecast(ForecastPublishedEvent e) {
        safe(() -> syncForecasts());
    }

    /** 需求池维护失败不影响销售单据操作（每天对账会修正），只记录日志 */
    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            LOG.warn("需求池同步失败：{}", ex.getMessage(), ex);
        }
    }

    /** 按销售订单的实际数据更新该订单的全部需求（已存在的行 + 当前未出完的行） */
    public void syncOrder(Long orderId) {
        List<PmcDemandDO> existing = mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, SALES_ORDER)
                .eq(PmcDemandDO::getSourceId, orderId));
        Set<Long> lineIds = new HashSet<>();
        existing.forEach(d -> lineIds.add(d.getSourceLineId()));
        salesOrderQueryApi.getOpenLines(new OpenLineFilter(null, null, orderId, null, null)).forEach(l -> lineIds.add(l.lineId()));
        if (lineIds.isEmpty()) return;
        Map<Long, SalesOrderLineDTO> lines = salesOrderQueryApi.getLines(lineIds);
        Map<Long, PmcDemandDO> byLine = existing.stream().collect(Collectors.toMap(PmcDemandDO::getSourceLineId, d -> d, (a, b) -> a));
        for (Long lineId : lineIds) apply(byLine.get(lineId), lines.get(lineId));
    }

    private void apply(PmcDemandDO d, SalesOrderLineDTO l) {
        boolean open = l != null && ACTIVE_ORDER.contains(l.orderStatus()) && "OPEN".equals(l.lineStatus()) && PmcSupport.nz(l.openQty()).signum() > 0;
        if (d == null) {
            if (!open) return;
            d = new PmcDemandDO();
            d.setDemandType(SALES_ORDER);
            d.setSourceId(l.orderId());
            d.setSourceLineId(l.lineId());
            d.setPriority(5);
            d.setReplyStatus(l.promisedDate() == null ? REPLY_PENDING : REPLY_DONE);
            if (l.promisedDate() != null) {
                d.setRepliedQty(l.baseQty());
                d.setRepliedCustomerDate(l.requiredDate());
            }
        }
        if (l != null) {
            d.setSourceNo(l.orderNo());
            d.setSourceLineNo(l.lineNo());
            d.setCustomerId(l.customerId());
            d.setSalesOwnerId(l.ownerId());
            d.setMaterialId(l.materialId());
            d.setQty(l.baseQty());
            BigDecimal openQty = open ? PmcSupport.max0(l.openQty()) : BigDecimal.ZERO;
            d.setOpenQty(openQty);
            d.setFulfilledQty(PmcSupport.max0(l.baseQty().subtract(openQty)));
            d.setCustomerDate(l.requiredDate());
            d.setPromisedDate(l.promisedDate());
            d.setRequiredDate(l.promisedDate() != null ? l.promisedDate() : l.requiredDate());
            if (REPLY_DONE.equals(d.getReplyStatus()) && (d.getRepliedQty() == null || d.getRepliedQty().compareTo(l.baseQty()) != 0
                    || !Objects.equals(d.getRepliedCustomerDate(), l.requiredDate()))) {
                d.setReplyStatus(REPLY_AGAIN);
            } else if (REPLY_PENDING.equals(d.getReplyStatus()) && l.promisedDate() != null) {
                d.setReplyStatus(REPLY_DONE);
                d.setRepliedQty(l.baseQty());
                d.setRepliedCustomerDate(l.requiredDate());
            }
        } else {
            d.setOpenQty(BigDecimal.ZERO);
        }
        d.setDemandStatus(open ? OPEN : CLOSED);
        if (d.getId() == null) mapper.insert(d);
        else mapper.updateByIdOrFail(d);
    }

    /** 净预测：每个预测行一条需求，需求日期取该月 1 日；不再出现在净预测中的行关闭 */
    public void syncForecasts() {
        Map<Long, NetForecastDTO> net = forecastApi.getNetForecast(null, null).stream()
                .collect(Collectors.toMap(NetForecastDTO::lineId, n -> n, (a, b) -> a));
        List<PmcDemandDO> existing = mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, FORECAST)
                .eq(PmcDemandDO::getDemandStatus, OPEN));
        Map<Long, PmcDemandDO> byLine = new HashMap<>();
        for (PmcDemandDO d : existing) byLine.put(d.getSourceLineId(), d);
        for (NetForecastDTO n : net.values()) {
            PmcDemandDO d = byLine.remove(n.lineId());
            if (d == null) {
                d = mapper.selectOne(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, FORECAST)
                        .eq(PmcDemandDO::getSourceLineId, n.lineId()).last("LIMIT 1"));
            }
            if (d == null) {
                d = new PmcDemandDO();
                d.setDemandType(FORECAST);
                d.setSourceLineId(n.lineId());
                d.setPriority(5);
            }
            d.setSourceId(n.forecastId());
            d.setSourceNo(n.forecastNo());
            d.setCustomerId(n.customerId());
            d.setMaterialId(n.materialId());
            d.setQty(n.qty());
            d.setFulfilledQty(n.consumedQty());
            d.setOpenQty(n.netQty());
            LocalDate date = LocalDate.of(Integer.parseInt(n.period().substring(0, 4)), Integer.parseInt(n.period().substring(4, 6)), 1);
            d.setRequiredDate(date);
            d.setCustomerDate(date);
            d.setDemandStatus(n.netQty().signum() > 0 ? OPEN : CLOSED);
            if (d.getId() == null) mapper.insert(d);
            else mapper.updateByIdOrFail(d);
        }
        for (PmcDemandDO d : byLine.values()) {
            d.setOpenQty(BigDecimal.ZERO);
            d.setDemandStatus(CLOSED);
            mapper.updateByIdOrFail(d);
        }
    }

    /** 每天 01:30 全量对账：以销售订单、预测的实际数据重建未关闭的需求 */
    @ErpJob(code = "PMC_DEMAND_RECONCILE", name = "需求池对账", cron = "0 30 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String reconcile() {
        Set<Long> orderIds = new HashSet<>();
        mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, SALES_ORDER).eq(PmcDemandDO::getDemandStatus, OPEN)
                .select(PmcDemandDO::getSourceId)).forEach(d -> orderIds.add(d.getSourceId()));
        salesOrderQueryApi.getOpenLines(OpenLineFilter.all()).forEach(l -> orderIds.add(l.orderId()));
        orderIds.forEach(this::syncOrder);
        syncForecasts();
        return "对账销售订单 " + orderIds.size() + " 张";
    }

    // ==================== 手工需求 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(DemandSave req) {
        validate(req);
        PmcDemandDO d = new PmcDemandDO();
        d.setDemandType(MANUAL);
        fill(d, req);
        d.setFulfilledQty(BigDecimal.ZERO);
        d.setDemandStatus(OPEN);
        mapper.insert(d);
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DemandSave req) {
        PmcDemandDO d = getOrThrow(id);
        if (!MANUAL.equals(d.getDemandType())) throw new BizException(PmcErrorCodes.DEMAND_NOT_MANUAL);
        validate(req);
        fill(d, req);
        mapper.updateByIdOrFail(d);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        PmcDemandDO d = getOrThrow(id);
        if (!MANUAL.equals(d.getDemandType())) throw new BizException(PmcErrorCodes.DEMAND_NOT_MANUAL);
        d.setDemandStatus(CLOSED);
        d.setOpenQty(BigDecimal.ZERO);
        mapper.updateByIdOrFail(d);
    }

    private void validate(DemandSave req) {
        if (req.qty() == null || req.qty().signum() <= 0) throw new BizException(PmcErrorCodes.DEMAND_QTY_INVALID);
        if (!StringUtils.hasText(req.remark())) throw new BizException(PmcErrorCodes.DEMAND_REMARK_REQUIRED);
        support.materialApi().validateUsable(req.materialId());
    }

    private static void fill(PmcDemandDO d, DemandSave req) {
        d.setMaterialId(req.materialId());
        d.setQty(req.qty());
        d.setOpenQty(req.qty().subtract(PmcSupport.nz(d.getFulfilledQty())));
        d.setRequiredDate(req.requiredDate());
        d.setCustomerDate(req.requiredDate());
        d.setPriority(req.priority() == null ? 5 : Math.max(1, Math.min(9, req.priority())));
        d.setCustomerId(req.customerId());
        d.setRemark(req.remark().trim());
    }

    public PmcDemandDO getOrThrow(Long id) {
        PmcDemandDO d = id == null ? null : mapper.selectById(id);
        if (d == null) throw new BizException(PmcErrorCodes.DEMAND_NOT_EXISTS);
        return d;
    }

    // ==================== 查询 ====================

    /** 未关闭的需求（MRP、MPS 使用），按需求日期 */
    public List<PmcDemandDO> openDemands(LocalDate to) {
        return mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandStatus, OPEN).gt(PmcDemandDO::getOpenQty, 0)
                .le(to != null, PmcDemandDO::getRequiredDate, to).orderByAsc(PmcDemandDO::getRequiredDate).orderByAsc(PmcDemandDO::getPriority)
                .orderByAsc(PmcDemandDO::getId));
    }

    public PageResult<DemandRow> page(DemandQuery q) {
        LambdaQueryWrapper<PmcDemandDO> w = new LambdaQueryWrapper<PmcDemandDO>()
                .eq(q.getMaterialId() != null, PmcDemandDO::getMaterialId, q.getMaterialId())
                .eq(q.getCustomerId() != null, PmcDemandDO::getCustomerId, q.getCustomerId())
                .in(StringUtils.hasText(q.getDemandTypes()), PmcDemandDO::getDemandType, Arrays.asList(String.valueOf(q.getDemandTypes()).split(",")))
                .ge(q.getDateFrom() != null, PmcDemandDO::getRequiredDate, q.getDateFrom())
                .le(q.getDateTo() != null, PmcDemandDO::getRequiredDate, q.getDateTo());
        if (!Boolean.FALSE.equals(q.getOpenOnly())) w.eq(PmcDemandDO::getDemandStatus, OPEN);
        if (q.getPlannerId() != null) {
            Set<Long> mids = mapper.selectList(w.clone().select(PmcDemandDO::getMaterialId)).stream().map(PmcDemandDO::getMaterialId).collect(Collectors.toSet());
            Map<Long, MaterialPlanAttr> attrs = support.planAttrs(mids);
            List<Long> mine = mids.stream().filter(id -> attrs.containsKey(id) && q.getPlannerId().equals(attrs.get(id).plannerId())).toList();
            if (mine.isEmpty()) return new PageResult<>(List.of(), 0L);
            w.in(PmcDemandDO::getMaterialId, mine);
        }
        w.orderByAsc(PmcDemandDO::getRequiredDate).orderByAsc(PmcDemandDO::getPriority).orderByAsc(PmcDemandDO::getId);
        IPage<PmcDemandDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<DemandRow> rows(List<PmcDemandDO> list) {
        Set<Long> mids = list.stream().map(PmcDemandDO::getMaterialId).collect(Collectors.toSet());
        Map<Long, MaterialDTO> ms = support.materials(mids);
        Map<Long, BigDecimal> avail = data.available(mids);
        Map<Long, BigDecimal> wip = data.wip(mids);
        Map<Long, InTransitDTO> transit = data.inTransit(mids);
        Map<Long, CustomerDTO> cs = data.customers(list.stream().map(PmcDemandDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcDemandDO::getCreatedBy).toList());
        List<DemandRow> out = new ArrayList<>();
        for (PmcDemandDO d : list) {
            MaterialDTO m = ms.get(d.getMaterialId());
            InTransitDTO t = transit.get(d.getMaterialId());
            out.add(new DemandRow(d.getId(), d.getDemandType(), d.getSourceId(), d.getSourceNo(), d.getSourceLineNo(), d.getCustomerId(),
                    PlanningData.customerName(cs, d.getCustomerId()), d.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), d.getQty(), d.getFulfilledQty(), d.getOpenQty(), d.getRequiredDate(),
                    d.getCustomerDate(), d.getPromisedDate(), avail.getOrDefault(d.getMaterialId(), BigDecimal.ZERO),
                    wip.getOrDefault(d.getMaterialId(), BigDecimal.ZERO), t == null ? BigDecimal.ZERO : t.qty(), d.getPriority(), d.getDemandStatus(),
                    d.getRemark(), MANUAL.equals(d.getDemandType()) ? PmcSupport.name(users, d.getCreatedBy()) : null));
        }
        return out;
    }
}
