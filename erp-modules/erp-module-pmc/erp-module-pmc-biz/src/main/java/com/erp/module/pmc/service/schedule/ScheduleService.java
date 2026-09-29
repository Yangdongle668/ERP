package com.erp.module.pmc.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.controller.vo.ScheduleVOs.ApplyRow;
import com.erp.module.pmc.controller.vo.ScheduleVOs.RunResult;
import com.erp.module.pmc.controller.vo.ScheduleVOs.ScheduleRow;
import com.erp.module.pmc.controller.vo.ScheduleVOs.SimulateResult;
import com.erp.module.pmc.controller.vo.ScheduleVOs.SimulateRow;
import com.erp.module.pmc.dal.dataobject.PmcScheduleDO;
import com.erp.module.pmc.dal.mapper.PmcScheduleMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.schedule.CapacityService.Calendar;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.production.api.order.ProductionOrderApi;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 排产（需求 06-05 第 3 节）。有限产能：按 锁定 → 优先级 → 需求日期 → 计划开工 排序，每道工序从 max(今天, 上道结束) 起在工作中心逐日占用剩余产能；
 * 无限产能：从需求日期按工序倒排，不考虑已占用，只用于负荷分析。锁定的工序不移动。
 */
@Service("pmcScheduleService")
public class ScheduleService {

    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final int MAX_DAYS = 730;

    private final PmcScheduleMapper mapper;
    private final CapacityService capacityService;
    private final PlanningData data;
    private final PmcSupport support;
    private final SalesOrderQueryApi salesOrderQueryApi;
    private final ProductionOrderApi productionOrderApi;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScheduleService(PmcScheduleMapper mapper, CapacityService capacityService, PlanningData data, PmcSupport support,
                           SalesOrderQueryApi salesOrderQueryApi, ProductionOrderApi productionOrderApi) {
        this.mapper = mapper;
        this.capacityService = capacityService;
        this.data = data;
        this.support = support;
        this.salesOrderQueryApi = salesOrderQueryApi;
        this.productionOrderApi = productionOrderApi;
    }

    /** 待排的一道工序及排产结果 */
    static final class Op {
        OpenOrderDTO order;
        int seq;
        String operation;
        Long wcId;
        Long deptId;
        BigDecimal load;
        LocalDate due;
        int priority;
        LocalDateTime start;
        LocalDateTime end;
        Map<LocalDate, BigDecimal> loads = new LinkedHashMap<>();
        PmcScheduleDO locked;
    }

    // ==================== 数据准备 ====================

    private List<List<Op>> prepare(Long deptId, Map<Long, Integer> priorityOverride) {
        Set<Long> depts = deptId == null ? null : support.deptAndChildren(deptId);
        List<OpenOrderDTO> orders = data.productionQueryApi().getOpenOrders(null).stream()
                .filter(o -> !"SUSPENDED".equals(o.prodStatus()) && o.remainingQty().signum() > 0)
                .filter(o -> depts == null || depts.contains(o.deptId())).toList();
        Map<Long, SalesOrderLineDTO> lines = salesOrderQueryApi.getLines(orders.stream().map(OpenOrderDTO::salesOrderLineId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, WorkCenterDTO> wcs = data.workCenters();
        Map<String, PmcScheduleDO> locked = mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>().eq(PmcScheduleDO::getLocked, true)).stream()
                .collect(Collectors.toMap(s -> s.getProdOrderId() + "#" + s.getOperationSeq(), s -> s, (a, b) -> a));
        List<List<Op>> out = new ArrayList<>();
        for (OpenOrderDTO o : orders) {
            SalesOrderLineDTO sl = o.salesOrderLineId() == null ? null : lines.get(o.salesOrderLineId());
            LocalDate due = sl != null && sl.dueDate() != null ? sl.dueDate() : o.planEnd();
            int priority = priorityOverride.getOrDefault(o.id(), o.priority());
            List<Op> ops = new ArrayList<>();
            List<OpenOrderDTO.Operation> steps = o.operations();
            if (steps.isEmpty()) {
                RoutingDTO r = (o.routingId() != null ? data.routingApi().getRouting(o.routingId()) : data.routingApi().getDefaultRouting(o.materialId()))
                        .orElse(null);
                if (r != null) {
                    steps = r.steps().stream().map(s -> new OpenOrderDTO.Operation(s.seq(), s.operation(), s.workCenterId(), s.setupMinutes(),
                            s.runSeconds(), s.reportPoint(), BigDecimal.ZERO)).toList();
                }
            }
            for (OpenOrderDTO.Operation s : steps.stream().sorted(Comparator.comparingInt(OpenOrderDTO.Operation::seq)).toList()) {
                Op op = new Op();
                op.order = o;
                op.seq = s.seq();
                op.operation = s.operation();
                op.wcId = s.workCenterId();
                WorkCenterDTO w = s.workCenterId() == null ? null : wcs.get(s.workCenterId());
                op.deptId = w != null ? w.deptId() : o.deptId();
                op.load = CapacityService.loadHours(o.qty().subtract(PmcSupport.nz(s.doneQty())), s.stdSetupMinutes(), s.stdRunSeconds());
                op.due = due;
                op.priority = priority;
                op.locked = locked.get(o.id() + "#" + s.seq());
                ops.add(op);
            }
            if (!ops.isEmpty()) out.add(ops);
        }
        out.sort(Comparator.comparing((List<Op> l) -> l.stream().anyMatch(x -> x.locked != null) ? 0 : 1)
                .thenComparingInt(l -> l.get(0).priority).thenComparing(l -> l.get(0).due)
                .thenComparing(l -> l.get(0).order.planStart()).thenComparing(l -> l.get(0).order.docNo()));
        return out;
    }

    private String mode(String mode) {
        if (StringUtils.hasText(mode)) return mode;
        return support.params().getString(PmcModuleConfig.P_SCHEDULE_MODE);
    }

    // ==================== 计算 ====================

    private void compute(List<List<Op>> orders, String mode, Calendar cal, Map<Long, Map<LocalDate, BigDecimal>> used) {
        LocalDate today = LocalDate.now();
        for (List<Op> ops : orders) {
            for (Op op : ops) {
                if (op.locked == null) continue;
                op.start = op.locked.getSchedStart();
                op.end = op.locked.getSchedEnd();
                op.loads = CapacityService.parse(op.locked.getDayLoads());
                if (op.wcId != null) op.loads.forEach((d, h) -> used.computeIfAbsent(op.wcId, k -> new HashMap<>()).merge(d, h, BigDecimal::add));
            }
        }
        for (List<Op> ops : orders) {
            if ("INFINITE".equals(mode)) {
                backward(ops, cal);
            } else {
                LocalDateTime cursor = today.atTime(DAY_START);
                for (Op op : ops) {
                    if (op.locked != null) {
                        cursor = op.end;
                        continue;
                    }
                    forward(op, cursor, cal, used);
                    cursor = op.end;
                }
            }
        }
    }

    /** 有限产能顺排：从 from 所在日期开始逐日占用剩余产能，支持跨天拆分 */
    private void forward(Op op, LocalDateTime from, Calendar cal, Map<Long, Map<LocalDate, BigDecimal>> used) {
        op.loads = new LinkedHashMap<>();
        if (op.wcId == null || op.load.signum() <= 0) {
            op.start = from;
            op.end = from;
            return;
        }
        Map<LocalDate, BigDecimal> u = used.computeIfAbsent(op.wcId, k -> new HashMap<>());
        BigDecimal left = op.load;
        LocalDate d = from.toLocalDate();
        LocalDateTime start = null;
        LocalDateTime end = from;
        for (int i = 0; i < MAX_DAYS && left.signum() > 0; i++, d = d.plusDays(1)) {
            BigDecimal before = u.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal avail = cal.hours(op.wcId, d).subtract(before);
            if (avail.signum() <= 0) continue;
            BigDecimal take = avail.min(left);
            if (start == null) start = at(d, before);
            u.put(d, before.add(take));
            op.loads.put(d, take);
            left = left.subtract(take);
            end = at(d, before.add(take));
        }
        op.start = start == null ? from : start;
        op.end = end;
    }

    /** 无限产能倒排：末道工序在需求日期结束，按每天的全部产能向前占用 */
    private void backward(List<Op> ops, Calendar cal) {
        LocalDate cursor = ops.get(0).due;
        for (int k = ops.size() - 1; k >= 0; k--) {
            Op op = ops.get(k);
            if (op.locked != null) {
                cursor = op.start.toLocalDate();
                continue;
            }
            op.loads = new LinkedHashMap<>();
            if (op.wcId == null || op.load.signum() <= 0) {
                op.start = cursor.atTime(DAY_START);
                op.end = op.start;
                continue;
            }
            BigDecimal left = op.load;
            LocalDate d = cursor;
            LocalDate first = cursor;
            for (int i = 0; i < MAX_DAYS && left.signum() > 0; i++, d = d.minusDays(1)) {
                BigDecimal cap = cal.hours(op.wcId, d);
                if (cap.signum() <= 0) continue;
                BigDecimal take = cap.min(left);
                op.loads.put(d, take);
                left = left.subtract(take);
                first = d;
            }
            op.start = first.atTime(DAY_START);
            op.end = cursor.atTime(DAY_START).plusMinutes(minutes(op.loads.getOrDefault(cursor, BigDecimal.ZERO)));
            cursor = first;
        }
    }

    private static LocalDateTime at(LocalDate d, BigDecimal hours) {
        return d.atTime(DAY_START).plusMinutes(minutes(hours));
    }

    private static long minutes(BigDecimal hours) {
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static LocalDate orderEnd(List<Op> ops) {
        return ops.stream().map(o -> o.end.toLocalDate()).max(Comparator.naturalOrder()).orElse(null);
    }

    // ==================== 运行 / 模拟 ====================

    @Transactional(rollbackFor = Exception.class)
    public RunResult run(Long deptId, String mode) {
        return run(deptId, mode, Map.of(), null);
    }

    private RunResult run(Long deptId, String mode, Map<Long, Integer> override, Long lockOrderId) {
        if (!running.compareAndSet(false, true)) throw new BizException(PmcErrorCodes.SCHEDULE_RUNNING);
        try {
            String m = mode(mode);
            List<List<Op>> orders = prepare(deptId, override);
            LocalDate today = LocalDate.now();
            Calendar cal = capacityService.calendar(today.minusDays(MAX_DAYS), today.plusDays(MAX_DAYS));
            compute(orders, m, cal, new HashMap<>());
            Set<Long> ids = orders.stream().map(l -> l.get(0).order.id()).collect(Collectors.toSet());
            // 删除未锁定的旧结果（本次范围内的订单、已不再待排的订单）
            for (PmcScheduleDO s : mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>())) {
                boolean inScope = ids.contains(s.getProdOrderId()) || deptId == null;
                if (inScope && !Boolean.TRUE.equals(s.getLocked())) mapper.deleteById(s.getId());
            }
            int opCount = 0;
            int late = 0;
            for (List<Op> ops : orders) {
                LocalDate end = orderEnd(ops);
                boolean isLate = end != null && end.isAfter(ops.get(0).due);
                if (isLate) late++;
                for (Op op : ops) {
                    if (op.locked != null) {
                        op.locked.setIsLate(isLate);
                        op.locked.setDueDate(op.due);
                        mapper.updateByIdOrFail(op.locked);
                        continue;
                    }
                    PmcScheduleDO s = row(op, isLate);
                    s.setLocked(lockOrderId != null && lockOrderId.equals(op.order.id()));
                    mapper.insert(s);
                    opCount++;
                }
            }
            return new RunResult(orders.size(), opCount, late, m);
        } finally {
            running.set(false);
        }
    }

    private static PmcScheduleDO row(Op op, boolean late) {
        PmcScheduleDO s = new PmcScheduleDO();
        s.setProdOrderId(op.order.id());
        s.setProdOrderNo(op.order.docNo());
        s.setMaterialId(op.order.materialId());
        s.setOperationSeq(op.seq);
        s.setOperation(op.operation);
        s.setWorkCenterId(op.wcId);
        s.setDeptId(op.deptId);
        s.setSchedStart(op.start);
        s.setSchedEnd(op.end);
        s.setLoadHours(op.load);
        s.setDayLoads(CapacityService.format(op.loads));
        s.setDueDate(op.due);
        s.setIsLate(late);
        s.setPriority(op.priority);
        s.setLocked(false);
        s.setManual(false);
        s.setApplied(false);
        return s;
    }

    /** 插单模拟：不保存，按新优先级重排，列出因此延期的其他订单 */
    public SimulateResult simulate(Long prodOrderId, Integer priority) {
        Map<Long, LocalDate> before = currentEnds();
        List<List<Op>> orders = prepare(null, Map.of(prodOrderId, priority == null ? 1 : priority));
        List<List<Op>> sorted = new ArrayList<>(orders);
        // 同优先级时插单订单排在前面
        sorted.sort(Comparator.comparing((List<Op> l) -> l.stream().anyMatch(x -> x.locked != null) ? 0 : 1)
                .thenComparingInt(l -> l.get(0).priority).thenComparing(l -> l.get(0).order.id().equals(prodOrderId) ? 0 : 1)
                .thenComparing(l -> l.get(0).due).thenComparing(l -> l.get(0).order.planStart()));
        LocalDate today = LocalDate.now();
        compute(sorted, "FINITE", capacityService.calendar(today.minusDays(MAX_DAYS), today.plusDays(MAX_DAYS)), new HashMap<>());
        List<SimulateRow> delayed = new ArrayList<>();
        LocalDate newEnd = null;
        String no = null;
        Map<Long, MaterialDTO> ms = support.materials(sorted.stream().map(l -> l.get(0).order.materialId()).toList());
        for (List<Op> ops : sorted) {
            OpenOrderDTO o = ops.get(0).order;
            LocalDate end = orderEnd(ops);
            if (o.id().equals(prodOrderId)) {
                newEnd = end;
                no = o.docNo();
                continue;
            }
            LocalDate old = before.get(o.id());
            if (old != null && end != null && end.isAfter(old)) {
                delayed.add(new SimulateRow(o.id(), o.docNo(), PmcSupport.code(ms, o.materialId()), old, end, (int) ChronoUnit.DAYS.between(old, end),
                        ops.get(0).due, end.isAfter(ops.get(0).due)));
            }
        }
        return new SimulateResult(prodOrderId, no, newEnd, delayed);
    }

    /** 确认插单：按新优先级重排并锁定该订单的工序 */
    @Transactional(rollbackFor = Exception.class)
    public RunResult confirmInsert(Long prodOrderId, Integer priority) {
        return run(null, "FINITE", Map.of(prodOrderId, priority == null ? 1 : priority), prodOrderId);
    }

    private Map<Long, LocalDate> currentEnds() {
        Map<Long, LocalDate> map = new HashMap<>();
        for (PmcScheduleDO s : mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>())) {
            map.merge(s.getProdOrderId(), s.getSchedEnd().toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return map;
    }

    // ==================== 调整、锁定 ====================

    /** 拖拽：移到某工作中心、某天开始，后续工序顺延（锁定的不移动） */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long id, Long workCenterId, LocalDate startDate) {
        PmcScheduleDO s = getOrThrow(id);
        if (Boolean.TRUE.equals(s.getLocked())) throw new BizException(PmcErrorCodes.SCHEDULE_LOCKED);
        List<PmcScheduleDO> mine = mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>().eq(PmcScheduleDO::getProdOrderId, s.getProdOrderId())
                .orderByAsc(PmcScheduleDO::getOperationSeq));
        PmcScheduleDO prev = mine.stream().filter(x -> x.getOperationSeq() < s.getOperationSeq()).reduce((a, b) -> b).orElse(null);
        if (prev != null && startDate.isBefore(prev.getSchedEnd().toLocalDate())) throw new BizException(PmcErrorCodes.SCHEDULE_BEFORE_PREV);
        Map<Long, WorkCenterDTO> wcs = data.workCenters();
        if (workCenterId != null && !workCenterId.equals(s.getWorkCenterId())) {
            WorkCenterDTO to = wcs.get(workCenterId);
            WorkCenterDTO from = s.getWorkCenterId() == null ? null : wcs.get(s.getWorkCenterId());
            if (to == null || from != null && !Objects.equals(from.deptId(), to.deptId())) throw new BizException(PmcErrorCodes.SCHEDULE_WC_INVALID);
            s.setWorkCenterId(workCenterId);
        }
        List<PmcScheduleDO> moving = mine.stream().filter(x -> x.getOperationSeq() >= s.getOperationSeq() && !Boolean.TRUE.equals(x.getLocked())).toList();
        Set<Long> movingIds = moving.stream().map(PmcScheduleDO::getId).collect(Collectors.toSet());
        Map<Long, Map<LocalDate, BigDecimal>> used = new HashMap<>();
        for (PmcScheduleDO x : mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>())) {
            if (movingIds.contains(x.getId()) || x.getWorkCenterId() == null) continue;
            CapacityService.parse(x.getDayLoads()).forEach((d, h) -> used.computeIfAbsent(x.getWorkCenterId(), k -> new HashMap<>()).merge(d, h, BigDecimal::add));
        }
        Calendar cal = capacityService.calendar(startDate.minusDays(1), startDate.plusDays(MAX_DAYS));
        LocalDateTime cursor = startDate.atTime(DAY_START);
        for (PmcScheduleDO x : moving) {
            Op op = new Op();
            op.wcId = x.getId().equals(s.getId()) ? s.getWorkCenterId() : x.getWorkCenterId();
            op.load = x.getLoadHours();
            LocalDateTime from = cursor;
            if (!x.getId().equals(s.getId()) && x.getSchedStart().isAfter(cursor)) from = x.getSchedStart();
            forward(op, from, cal, used);
            x.setWorkCenterId(op.wcId);
            x.setSchedStart(op.start);
            x.setSchedEnd(op.end);
            x.setDayLoads(CapacityService.format(op.loads));
            x.setManual(true);
            x.setApplied(false);
            cursor = op.end;
        }
        LocalDate end = mine.stream().map(x -> moving.stream().filter(m -> m.getId().equals(x.getId())).findFirst().orElse(x).getSchedEnd().toLocalDate())
                .max(Comparator.naturalOrder()).orElse(null);
        for (PmcScheduleDO x : moving) {
            x.setIsLate(end != null && x.getDueDate() != null && end.isAfter(x.getDueDate()));
            mapper.updateByIdOrFail(x);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id, boolean locked) {
        PmcScheduleDO s = getOrThrow(id);
        s.setLocked(locked);
        mapper.updateByIdOrFail(s);
    }

    private PmcScheduleDO getOrThrow(Long id) {
        PmcScheduleDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw new BizException(PmcErrorCodes.SCHEDULE_NOT_EXISTS);
        return s;
    }

    // ==================== 查询、应用 ====================

    public List<ScheduleRow> list(LocalDate from, LocalDate to, Long deptId) {
        LocalDate f = from == null ? LocalDate.now().minusDays(3) : from;
        LocalDate t = to == null ? f.plusDays(30) : to;
        Set<Long> depts = deptId == null ? null : support.deptAndChildren(deptId);
        List<PmcScheduleDO> list = mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>().le(PmcScheduleDO::getSchedStart, t.plusDays(1).atStartOfDay())
                .ge(PmcScheduleDO::getSchedEnd, f.atStartOfDay()).orderByAsc(PmcScheduleDO::getWorkCenterId).orderByAsc(PmcScheduleDO::getSchedStart))
                .stream().filter(s -> depts == null || depts.contains(s.getDeptId())).toList();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PmcScheduleDO::getMaterialId).toList());
        Map<Long, WorkCenterDTO> wcs = data.workCenters();
        Map<Long, OpenOrderDTO> orders = data.productionQueryApi().getOpenOrders(null).stream().collect(Collectors.toMap(OpenOrderDTO::id, o -> o));
        return list.stream().map(s -> {
            MaterialDTO m = ms.get(s.getMaterialId());
            WorkCenterDTO w = s.getWorkCenterId() == null ? null : wcs.get(s.getWorkCenterId());
            OpenOrderDTO o = orders.get(s.getProdOrderId());
            return new ScheduleRow(s.getId(), s.getProdOrderId(), s.getProdOrderNo(), s.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), o == null ? null : o.qty(), s.getOperationSeq(), s.getOperation(), s.getWorkCenterId(),
                    w == null ? null : w.name(), s.getDeptId(), s.getSchedStart(), s.getSchedEnd(), s.getLoadHours(), s.getDueDate(),
                    Boolean.TRUE.equals(s.getIsLate()), s.getPriority(), Boolean.TRUE.equals(s.getLocked()), Boolean.TRUE.equals(s.getManual()),
                    Boolean.TRUE.equals(s.getApplied()), o == null ? null : o.prodStatus());
        }).toList();
    }

    /** 应用预览：排产开工 / 完工与生产订单计划日期不同的订单 */
    public List<ApplyRow> applyPreview() {
        Map<Long, List<PmcScheduleDO>> byOrder = mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>()).stream()
                .collect(Collectors.groupingBy(PmcScheduleDO::getProdOrderId));
        Map<Long, OpenOrderDTO> orders = data.productionQueryApi().getOpenOrders(null).stream().collect(Collectors.toMap(OpenOrderDTO::id, o -> o));
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(OpenOrderDTO::materialId).toList());
        List<ApplyRow> out = new ArrayList<>();
        byOrder.forEach((id, rows) -> {
            OpenOrderDTO o = orders.get(id);
            if (o == null || rows.stream().allMatch(r -> Boolean.TRUE.equals(r.getLocked()))) return;
            LocalDate start = rows.stream().map(r -> r.getSchedStart().toLocalDate()).min(Comparator.naturalOrder()).orElse(null);
            LocalDate end = rows.stream().map(r -> r.getSchedEnd().toLocalDate()).max(Comparator.naturalOrder()).orElse(null);
            if (start == null || start.equals(o.planStart()) && end.equals(o.planEnd())) return;
            out.add(new ApplyRow(id, o.docNo(), PmcSupport.code(ms, o.materialId()), o.planStart(), o.planEnd(), start, end, o.prodStatus()));
        });
        out.sort(Comparator.comparing(ApplyRow::prodOrderNo));
        return out;
    }

    /** 应用到生产订单：把排产的开工 / 完工写回计划日期（已下达的订单由生产记录操作日志） */
    @Transactional(rollbackFor = Exception.class)
    public int apply(List<Long> prodOrderIds) {
        int n = 0;
        for (ApplyRow r : applyPreview()) {
            if (prodOrderIds != null && !prodOrderIds.isEmpty() && !prodOrderIds.contains(r.prodOrderId())) continue;
            productionOrderApi.updatePlanDates(r.prodOrderId(), r.newStart(), r.newEnd(), "PMC 排产");
            for (PmcScheduleDO s : mapper.selectList(new LambdaQueryWrapper<PmcScheduleDO>().eq(PmcScheduleDO::getProdOrderId, r.prodOrderId()))) {
                s.setApplied(true);
                mapper.updateByIdOrFail(s);
            }
            n++;
        }
        return n;
    }

    /** 某订单的排产完工日期（交期预警使用），没有排产结果返回空 */
    public Map<Long, LocalDate> scheduledEnds() {
        return currentEnds();
    }
}
